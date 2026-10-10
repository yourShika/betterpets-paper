#!/usr/bin/env python3
"""Puts a collection of Blockbench pet models onto a server, ready for Better Pets.

    python scripts/install_models.py <folder with model .zip files> <server>/plugins/BetterPets [--dry-run]

Better Pets shows a pet as an animated 3D model when the BetterModel plugin is installed and a .bbmodel
for the pet lies in plugins/BetterPets/models. This script fills that folder from model packages:

* it looks through every .zip in the given folder for .bbmodel files (wherever in the archive they lie);
* for each pet in pets.yml and each of its skins it picks the model the plugin would pick - the same
  name rules as PetModelService: <pet>, <pet>__<skin> or <pet>_<skin>, optionally ending in _grounded or
  _flying, where the model-flying-pets list decides between the two - and copies only those, so that a
  pet that comes both walking and flying does not load twice;
* it measures every chosen model and writes a size for the pets that would stand out as far too big or
  too small next to the others into model-scale in config.yml;
* it switches the BetterModel module on (experimental-modules, modules.yml).

The models themselves are not part of Better Pets and are not distributed with it; this only installs
what you have. Run it with the server stopped, then start the server: BetterModel builds its resource
pack from the models (plugins/BetterModel/build.zip) - how that pack reaches the players is set up in
BetterModel's own config.
"""
import json
import os
import re
import sys
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
PETS_YML = os.path.join(HERE, '..', 'src', 'main', 'resources', 'pets.yml')
CONFIG_YML = os.path.join(HERE, '..', 'src', 'main', 'resources', 'config.yml')

# Sizes are in model pixels, 16 to a block. A pet's larger side is brought into this range.
SMALLEST, LARGEST = 11.0, 26.0
SCALE_MIN, SCALE_MAX = 0.55, 1.7


def normalize(name):
    return re.sub(r'[^a-z0-9_.-]', '_', name.lower().strip())


def read_pets():
    """pet id -> list of skin keys, read without a YAML library (the file is regular enough)."""
    pets, current, in_variants = {}, None, False
    for line in open(PETS_YML, encoding='utf-8'):
        if re.match(r'^  [a-z0-9_]+:\s*$', line):
            current = line.strip()[:-1]
            pets[current] = []
            in_variants = False
        elif current and re.match(r'^    variants:\s*$', line):
            in_variants = True
        elif in_variants and re.match(r'^      [A-Za-z0-9_]+:', line):
            pets[current].append(line.strip().split(':')[0])
        elif re.match(r'^    [a-z-]+:', line):
            in_variants = False
    return pets


def read_flying_pets():
    text = open(CONFIG_YML, encoding='utf-8').read()
    found = re.search(r'^model-flying-pets:\s*\[(.*?)\]', text, re.S | re.M)
    return {normalize(entry) for entry in found.group(1).split(',')} if found else set()


def resolve(pet, skin, flies, available):
    """The same search as PetModelService#modelName."""
    endings = ['_flying', '', '_grounded'] if flies else ['_grounded', '', '_flying']
    if skin:
        for ending in endings:
            for joint in ('__', '_'):
                if pet + joint + skin + ending in available:
                    return pet + joint + skin + ending
    for ending in endings:
        if pet + ending in available:
            return pet + ending
    return None


def measure(data):
    low, high = [1e9] * 3, [-1e9] * 3
    for element in data.get('elements', []):
        if 'from' in element and 'to' in element:
            for axis in range(3):
                low[axis] = min(low[axis], element['from'][axis], element['to'][axis])
                high[axis] = max(high[axis], element['from'][axis], element['to'][axis])
    return max(high[1] - low[1], high[0] - low[0], high[2] - low[2]) if high[0] > low[0] else 16.0


def set_key(text, key, value):
    """Sets a top-level key of a YAML text, keeping everything else (comments included) as it is."""
    pattern = re.compile(r'^' + re.escape(key) + r':.*?(?=^\S|\Z)', re.S | re.M)
    block = key + ': ' + value.rstrip('\n') + '\n'
    return pattern.sub(lambda _: block, text, count=1) if pattern.search(text) else text.rstrip('\n') + '\n' + block


def main():
    args = [arg for arg in sys.argv[1:] if not arg.startswith('--')]
    dry = '--dry-run' in sys.argv
    if len(args) != 2:
        sys.exit(__doc__)
    source, target = args
    pets, flying = read_pets(), read_flying_pets()

    # every model on offer: name -> (zip, member)
    offered = {}
    for archive in sorted(os.listdir(source)):
        if not archive.lower().endswith('.zip'):
            continue
        with zipfile.ZipFile(os.path.join(source, archive)) as bundle:
            for member in bundle.namelist():
                if member.lower().endswith('.bbmodel'):
                    offered.setdefault(normalize(os.path.basename(member)[:-len('.bbmodel')]), (archive, member))

    chosen, without_model, plain_only = {}, [], []
    for pet, skins in sorted(pets.items()):
        base = resolve(pet, None, pet in flying, offered)
        if base is None and not any(resolve(pet, normalize(skin), pet in flying, offered) for skin in skins):
            without_model.append(pet)
            continue
        if base:
            chosen[base] = pet
        for skin in skins:
            model = resolve(pet, normalize(skin), pet in flying, offered)
            if model:
                chosen[model] = pet
                if model == base:
                    plain_only.append(pet + ':' + skin)

    print(f'{len(offered)} models on offer, {len(chosen)} needed for {len(pets) - len(without_model)} of {len(pets)} pets.')
    if without_model:
        print('No model at all (these keep their head):', ', '.join(without_model))
    if plain_only:
        print(f'{len(plain_only)} skins without a model of their own use the plain one:', ', '.join(plain_only[:12]),
              '...' if len(plain_only) > 12 else '')

    models_dir = os.path.join(target, 'models')
    sizes = {}
    if not dry:
        os.makedirs(models_dir, exist_ok=True)
    for model, pet in sorted(chosen.items()):
        archive, member = offered[model]
        with zipfile.ZipFile(os.path.join(source, archive)) as bundle:
            raw = bundle.read(member)
        sizes.setdefault(pet, []).append(measure(json.loads(raw)))
        if not dry:
            with open(os.path.join(models_dir, model + '.bbmodel'), 'wb') as out:
                out.write(raw)

    scales = {}
    for pet, measured in sorted(sizes.items()):
        size = sorted(measured)[len(measured) // 2]
        wanted = min(LARGEST, max(SMALLEST, size))
        scale = round(min(SCALE_MAX, max(SCALE_MIN, wanted / size)), 2)
        if abs(scale - 1.0) >= 0.05:
            scales[pet] = scale
    print('Sizes:', ', '.join(f'{pet} x{scale}' for pet, scale in scales.items()) or 'all as they are')

    if dry:
        return
    config_path = os.path.join(target, 'config.yml')
    if os.path.isfile(config_path):
        text = open(config_path, encoding='utf-8').read()
        newline = '\r\n' if '\r\n' in text else '\n'
        text = text.replace('\r\n', '\n')
        text = set_key(text, 'experimental-modules', 'true')
        block = '\n  default: 1.0\n  pets:' + (''.join(f'\n    {pet}: {scale}' for pet, scale in scales.items()) or ' {}') + '\n  models: {}'
        text = set_key(text, 'model-scale', block)
        open(config_path, 'w', encoding='utf-8', newline='').write(text.replace('\n', newline))
    else:
        print('No config.yml there yet: start the server once, then run this again for the sizes.')
    modules_path = os.path.join(target, 'modules.yml')
    modules = open(modules_path, encoding='utf-8').read() if os.path.isfile(modules_path) else 'modules:\n'
    if re.search(r'^  bettermodel:\n    enabled: \w+', modules, re.M):
        modules = re.sub(r'(^  bettermodel:\n    enabled: )\w+', r'\1true', modules, flags=re.M)
    else:
        modules = modules.rstrip('\n') + '\n  bettermodel:\n    enabled: true\n'
    open(modules_path, 'w', encoding='utf-8').write(modules)
    print(f'Installed into {models_dir}.')


if __name__ == '__main__':
    main()

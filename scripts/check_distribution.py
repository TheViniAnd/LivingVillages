#!/usr/bin/env python3
"""Check the standalone Free source and optional production JAR using only Python's standard library."""
from pathlib import Path
import sys, zipfile

ROOT = Path(__file__).resolve().parents[1]
PAID_CLASSES = {
    'Military', 'MilitaryLedger', 'Diplomacy', 'Sieges', 'Defenses', 'FortGeometry',
    'RareItems', 'RareCrafts', 'Weapons', 'Landmarks', 'CulturalArchitecture',
    'IdentityMenus', 'Renovations', 'Architecture', 'Husbandry', 'Livestock',
    'Hunting', 'Raids', 'Leadership', 'Reserves', 'Permissions', 'BlueprintUpdates',
}
RESOURCES = {'config.yml', 'cultures.yml', 'buildings.yml', 'dialogues.yml', 'economy.yml',
             'layouts.yml', 'names.yml', 'plugin.yml', 'lang/en.json', 'lang/ru.json'}


def check():
    assert (ROOT / 'LICENSE').read_text().startswith('MIT License'), 'MIT license missing'
    for path in (ROOT / 'src').rglob('*'):
        assert path.name != 'edition.properties', 'Selectable bundled edition found'
        assert not (path.suffix == '.java' and path.stem in PAID_CLASSES), path
        assert not (path.is_file() and path.suffix in {'.db', '.jar', '.zip'}), path
    resources = {p.relative_to(ROOT / 'src/main/resources').as_posix()
                 for p in (ROOT / 'src/main/resources').rglob('*') if p.is_file()}
    assert resources == RESOURCES, f'Unexpected/missing content: {resources ^ RESOURCES}'
    assert 'PREMIUM' not in (ROOT / 'src/main/java/ru/livingvillages/domain/Edition.java').read_text()
    if len(sys.argv) > 1:
        jar = Path(sys.argv[1])
        with zipfile.ZipFile(jar) as archive:
            names = set(archive.namelist())
            assert 'edition.properties' not in names
            assert 'META-INF/LICENSE' in names
            assert 'ru/livingvillages/persistence/FreeSaveGuard.class' in names
            for name in names:
                if name.startswith('ru/livingvillages/') and name.endswith('.class'):
                    cls = name.rsplit('/', 1)[-1].split('$', 1)[0].removesuffix('.class')
                    assert cls not in PAID_CLASSES, name
                    assert 'Checks' not in cls and 'Probe' not in cls, f'QA fixture in public JAR: {name}'
                assert not name.endswith(('premium.yml', 'premium-buildings.yml', 'weapons.yml', 'rare-items.yml', 'edition.properties')), name
            descriptor = archive.read('plugin.yml').decode()
            assert 'name: LivingVillages\n' in descriptor and "version: '0.1'" in descriptor
        print(f'FREE_DISTRIBUTION_PASS: {jar.name}')
    else:
        print('FREE_SOURCE_BOUNDARY_PASS')


if __name__ == '__main__':
    check()

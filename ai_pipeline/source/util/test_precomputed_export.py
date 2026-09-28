"""CPU-only export -> restore -> prepared-input -> ZIP compatibility check."""
import copy
import shutil
import unittest
import zipfile

import cv2
import numpy as np
from source.service import test_prepost as fixtures
from source.service import prepost
from source.util import precomputed_export as packing, restore_precomputed_arrays as restore
from source.util import removal_experiments as ex


class ExportTest(unittest.TestCase):
    def test_lossless_export_restore_and_download(self):
        fixtures.PrePostTest.setUpClass()
        fixture = fixtures.PrePostTest()
        root,asset,_,_ = fixture.fixture()
        source=root/'source.mp4';shutil.copy2(ex.local(asset['record']['source']),source)
        asset.update(version=1,status='complete',source=prepost.relative(source),source_sha256=ex.digest(source),
            implementation='cpu-test',directory=prepost.relative(root),manifest=prepost.relative(root/'manifest.json'),
            music=None,timings={})
        asset['files']={prepost.relative(p):packing._receipt(p) for p in root.rglob('*') if p.is_file()}
        for key,person in asset['people'].items():person.update(id=int(key),directory=str(ex.local(person['masks']).parent.relative_to(ex.ROOT)))
        ex.write_json(root/'manifest.json',asset)
        group=ex.unique_dir('output/precomputed_export_checks','cpu');destination=group/'precomputed_test'
        before={p:ex.digest(p) for p in (ex.local(name) for name in asset['files'])}
        result=packing.export_package(asset,destination)
        self.assertEqual(result['status'],'compressed')
        self.assertEqual({p:ex.digest(p) for p in before},before)
        self.assertTrue(packing.export_package(asset,destination)['reused'])
        saved=packing.verify_package(destination)
        self.assertNotIn('background',saved['people']['1'])
        self.assertFalse((destination/'tracking').exists())
        original_pixel=cv2.imread(str(root/'frames/000003.png'),-1)
        np.testing.assert_array_equal(cv2.imread(str(destination/'frames/000003.png'),-1),original_pixel)
        self.assertTrue(ex.has_audio(destination/'original.mp4'))
        archive=packing.create_download(group)
        unpack=root/'unpacked'
        with zipfile.ZipFile(ex.local(archive['path'])) as zipped:
            self.assertIsNone(zipped.testzip())
            zipped.extractall(unpack)
        package=unpack/group.name/destination.name
        storage=ex.read_json(package/'storage.json')
        restore.restore(package)
        for name,record in storage['arrays'].items():
            self.assertEqual(ex.digest(package/name),record['original']['sha256'])
        restored=ex.read_json(package/'manifest.json')
        self.assertEqual(restored['status'],'complete')
        self.assertFalse((package/'storage.json').exists())
        rebased=copy.deepcopy(restored)
        rel=lambda name:str((package/name).relative_to(ex.ROOT))
        for key in ('source','camera','depth','text','manifest'):rebased[key]=rel(rebased[key])
        for key in ('source','frame_dir'):rebased['record'][key]=rel(rebased['record'][key])
        for person in rebased['people'].values():
            for key in packing.PERSON_PATHS:person[key]=rel(person[key])
        for removed,driver in ((1,2),(2,1)):
            out=root/f'post_{removed}';out.mkdir()
            record,original,background,body,common,b_masks,crop,lighting=prepost.materialize_segment(
                rebased,dict(first=2,stop=6),removed,driver,out)
            self.assertEqual(record['frames'],4)
            for folder in (body,common,b_masks):ex.validate_masks(folder,record)
            self.assertTrue(ex.has_audio(background))
            self.assertEqual(len(ex.sequence(crop,4)),4)


if __name__=='__main__':unittest.main()

"""CPU-only checks for cache gating, time alignment, prepared paths and final media."""
import contextlib
import io
import json
from pathlib import Path
import unittest
from unittest.mock import patch
import wave
from types import SimpleNamespace
import sys

import cv2
import numpy as np
from music_sync import core
from source.service import prepost as s
from source.util import removal_experiments as ex, music_sync
from source.enhancement import geometry_placement as gp, anatomical, foot_contact, scene_motion, depth_occlusion as depth
from source.relighting import composite as cp, variants, quality


class PrePostTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.root=ex.unique_dir('work/prepost_checks','cpu')

    def folder(self,name):
        return ex.unique_dir(self.root,name)

    def test_missing_incomplete_stale_and_changed_assets_never_launch(self):
        root=self.folder('availability');source=root/'source.mp4';source.write_bytes(b'not decoded by availability')
        library=root/'library';folder=library/ex.digest(source);folder.mkdir(parents=True)
        file=folder/'saved.npy';np.save(file,np.arange(4))
        manifest=folder/'manifest.json'
        data=dict(status='building',source_sha256=ex.digest(source),implementation='test-v1',people={'1':{},'2':{}},
                  manifest=s.relative(manifest),files={s.relative(file):dict(stat=[file.stat().st_size,file.stat().st_mtime_ns],sha256=ex.digest(file))})
        def save():
            ex.write_json(manifest,data)
            ex.write_json(folder/'current.json',dict(manifest=s.relative(manifest),sha256=ex.digest(manifest)))
        with patch.object(s,'LIBRARY',s.relative(library)),patch.object(s,'fingerprint',return_value='test-v1'),patch.object(s,'launch',side_effect=AssertionError('must not launch')):
            output=io.StringIO()
            with contextlib.redirect_stdout(output):
                self.assertIsNone(s.run_post(source,source,remove_id=1,driver_id=2))
            self.assertEqual(output.getvalue().strip(),s.MISSING)
            save();self.assertIsNone(s.prepared(source))
            data['status']='complete';save()
            self.assertIsNotNone(s.prepared(source,remove_id=1,driver_id=2,deep=True))
            self.assertIsNone(s.prepared(source,remove_id=8,driver_id=2))
            self.assertIsNone(s.prepared(source,remove_id=1,driver_id=1))
            with patch.object(s,'fingerprint',return_value='test-v2'):self.assertIsNone(s.prepared(source))
            np.save(file,np.arange(5));self.assertIsNone(s.prepared(source))

    def test_cached_music_features_and_subframe_offsets(self):
        a=np.random.default_rng(43).normal(size=core.SR*8).astype(np.float32)
        b=a[1600:];features=core.features(b)
        expected=core.find_offset(a,b)
        with patch.object(core,'features',wraps=core.features) as calculation:
            actual=core.find_offset(a,b,background_features=features)
            self.assertEqual(calculation.call_count,1)
        np.testing.assert_allclose(actual,expected,atol=1e-12)
        asset=dict(record=dict(fps=30.,frames=300),music='unused')
        with patch.object(s,'arrays',return_value=dict(audio=b,features=features,duration=10.)),patch.object(core,'read_video',return_value=(a,8.)),patch.object(core,'find_offset',return_value=(-.237,.9)):
            match=s.match_segment(asset,'video/AESPA_Serenade_user.mp4')
        self.assertEqual(match['first'],8)
        self.assertAlmostEqual(match['foreground_start']-match['background_start'],-.237)
        self.assertLess(match['foreground_start'],1/30)

    def test_remaining_background_person_is_selected_for_either_removal(self):
        asset=dict(people={'1':{},'2':{}})
        self.assertEqual(s.driver_for(asset,1),2)
        self.assertEqual(s.driver_for(asset,2),1)
        with self.assertRaises(ValueError):s.driver_for(dict(people={'1':{}}),1)

    def fixture(self):
        root=self.folder('media');n=8;fps=10.;shape=(64,64)
        frame_dir=root/'frames';frame_dir.mkdir();paths=[]
        for i in range(n):
            image=np.full((*shape,3),60+i*12,np.uint8)
            image[:,::4,1]+=15
            path=frame_dir/f'{i:06d}.png';cv2.imwrite(str(path),image);paths.append(path)
        audio=root/'audio.wav'
        with wave.open(str(audio),'wb') as f:
            f.setnchannels(1);f.setsampwidth(2);f.setframerate(48000)
            f.writeframes((np.sin(np.arange(round(n/fps*48000))*2*np.pi*440/48000)*8000).astype('<i2').tobytes())
        master=root/'original.mp4';ex.encode_frames((cv2.imread(str(p)) for p in paths),master,fps,audio_source=audio)
        pose=np.zeros((n,18,3),np.float32);pose[:,:,2]=1.
        points=[[30,8],[30,18],[22,18],[18,28],[16,36],[38,18],[42,28],[44,36],
                [24,36],[23,46],[22,57],[36,36],[37,46],[38,57],[28,7],[32,7],[26,8],[34,8]]
        pose[:,:,:2]=points
        geom=dict(unit_scale=np.full(n,30.),anchor=np.tile([30.,57.],(n,1)),feet=np.tile([[38.,57.],[22.,57.]],(n,1,1)),
                  height=np.array(1.7),contact=np.ones((n,2)),confidence=np.ones(n))
        camera=root/'camera.npz';np.savez(camera,matrix=np.tile(np.eye(3),(n,1,1)),valid=np.ones(n,bool))
        z=root/'depth.npy';np.save(z,np.full((n,*shape),2.,np.float32))
        text=root/'text.npz';np.savez(text,conditional=np.zeros((1,77,768),np.float16),unconditional=np.zeros((1,77,768),np.float16))
        people={}
        for id in (1,2):
            p=root/f'person_{id}';p.mkdir()
            for folder in ('masks','crop','removal_masks','condition'):(p/folder).mkdir()
            for i,image_path in enumerate(paths):
                mask=np.zeros(shape,np.uint8);mask[5:60,14:47]=255
                for folder in ('masks','removal_masks'):cv2.imwrite(str(p/folder/f'{i:06d}.png'),mask)
                cv2.imwrite(str(p/'crop'/f'{i:06d}.png'),np.dstack([cv2.imread(str(image_path)),mask]))
                cv2.imwrite(str(p/'condition'/f'{i:06d}.png'),variants.letterbox(cv2.imread(str(image_path)),[512,512])[0])
            np.save(p/'pose.npy',pose);np.save(p/'driver_pose.npy',pose)
            from source.enhancement import correction
            vectors,valid=correction.features(pose);np.savez(p/'features.npz',vectors=vectors,valid=valid)
            np.savez(p/'geometry.npz',**geom)
            np.savez(p/'reference.npz',feet=geom['feet'],feet_valid=np.ones((n,2),bool),
                     ground_x=np.full(n,30.),ground_y=np.full(n,57.),support_x=np.zeros(n,int),support_y=np.zeros(n,int))
            np.savez(p/'parts.npz',values=np.ones((n,10)),noise=np.zeros((n,10)),spans=np.ones(n))
            np.save(p/'grain.npy',np.zeros(n));np.save(p/'latents.npy',np.zeros((n,4,64,64),np.float16))
            person={key:s.relative(p/file) for key,file in dict(masks='masks',crop='crop',removal_masks='removal_masks',pose='pose.npy',driver_pose='driver_pose.npy',features='features.npz',geometry='geometry.npz',reference='reference.npz',parts='parts.npz',condition_frames='condition',latents='latents.npy',grain='grain.npy').items()}
            person['background_frames']=s.relative(frame_dir);people[str(id)]=person
        info,_=s.rel.video_info(master)
        record=dict(info,source=s.relative(master),source_sha256=ex.digest(master),frame_dir=s.relative(frame_dir))
        asset=dict(people=people,record=record,camera=s.relative(camera),depth=s.relative(z),depth_sha256=ex.digest(z),text=s.relative(text),manifest='test fixture')
        return root,asset,pose,geom

    def test_segment_reuses_background_analysis_and_encodes_final_with_audio(self):
        root,asset,pose,geom=self.fixture();out=root/'post';out.mkdir()
        record,bg,ready,body,common,bm,bc,lighting=s.materialize_segment(asset,dict(first=2,stop=6),1,2,out)
        self.assertEqual(record['frames'],4)
        np.testing.assert_equal(np.load(ex.local(record['prepared_background']['pose'])),pose[2:6])
        self.assertTrue(ex.has_audio(bg))
        np.testing.assert_equal(cv2.imread(str(body/'000000.png'),0),cv2.imread(str(ex.local(asset['people']['1']['masks'])/'000002.png'),0))
        # Exercise native foreground trimming as well (timestamps and audio survive).
        fg_video=out/'foreground.mp4';native=music_sync._trim_native(bg,fg_video,0.,.4)
        self.assertEqual(native['source_frames'],4)
        fg_parent=out/'fg';fg_parent.mkdir()
        original=s.sequence_view(bc,fg_parent/'original',0,4)
        s.sequence_view(body,fg_parent/'masks',0,4)
        corrected=s.sequence_view(bc,fg_parent/'corrected',0,4)
        dance_work=out/'dance';dance_work.mkdir();np.savez(dance_work/'alignment.npz',source_frames=np.arange(4),baseline_frames=np.arange(4))
        plan=dict(background=s.relative(bg),foreground=s.relative(original),foreground_video=s.relative(fg_video),
                  corrected=s.relative(corrected),work=s.relative(dance_work),driver=s.relative(bc),driver_masks=s.relative(bm),driver_id=2,
                  driver_source_sha256=record['source_sha256'],mirror=False,input_hashes={s.relative(fg_video):ex.digest(fg_video)})
        plan['input_hashes'].update({s.relative(p):ex.digest(p) for p in ex.sequence(original)})
        poses=out/'poses.npz';np.savez(poses,background=pose[2:6],foreground=pose[2:6])
        def foreground_track(source,masks,gpu):
            self.assertEqual(ex.local(source),fg_video)
            return {k:v[2:6] if v.ndim else v for k,v in geom.items()},ex.local(record['prepared_background']['geometry'])
        def foreground_camera(source,masks,**kwargs):
            self.assertEqual(ex.local(source),fg_video)
            return s.arrays(record['prepared_background']['camera']),ex.local(record['prepared_background']['camera'])
        with patch.object(gp,'require_backend'),patch.object(gp,'_track',side_effect=foreground_track) as track,patch.object(scene_motion,'track',side_effect=foreground_camera),patch.object(gp.importlib,'reload',side_effect=lambda value:value),patch.object(anatomical,'poses',return_value=(pose[2:6],pose[2:6],poses)):
            placed=gp.estimate(corrected,ready,body,record,plan=plan,gpu=1,preserve_foreground_height=True)
        self.assertEqual(track.call_count,1)
        self.assertEqual(placed['contact']['background_ground_y'],[57.]*4)
        with patch.object(depth,'scene_depth',side_effect=AssertionError('background depth must not run')),patch.object(depth,'part_depths',side_effect=AssertionError('background part analysis must not run')):
            occ=depth.prepare(plan,body,record,placement=placed,gpu=1)
        self.assertEqual(len(ex.sequence(occ/'front_masks')),4)
        render=out/'render';render.mkdir()
        obj=cp.render_object_video(ready,corrected,placed['transform'],render/'object.mp4',foreground_fps=10.,
            foreground_video=fg_video,source_masks=fg_parent/'masks',fg_shortfall='disappear',dof_match=False,
            motion_blur=False,edge_decontam=False,scene_harmonization=False)
        with patch.object(variants,'require_backend'):
            lp=variants.prepare(obj,bg,body,record,'RelightVid',conditioning_background=ready,prepared_background=lighting)
        work=ex.local(lp['work'])
        self.assertEqual(ex.digest(work/'background/000000.png'),ex.digest(ex.local(lighting['condition_frames'])/'000002.png'))
        s.sequence_view(lp['source_frames'],work/'relit_frames',0,4)
        lp.update(status='selected',choice=quality.evaluate(lp,work));ex.write_json(work/'plan.json',lp)
        with patch.object(cp,'_grain_sigma',side_effect=AssertionError('cached background noise must be used')):
            final=variants.finish(lp,output_path=out/'final.mp4',compose=True,occlusion_dir=occ)
        info,_=s.rel.video_info(final)
        self.assertEqual(info['frames'],4);self.assertAlmostEqual(info['fps'],10.)
        self.assertTrue(ex.has_audio(final))

    def test_pre_to_post_orchestration_with_model_calls_replaced(self):
        """Real asset publication/lookup/media/algorithms; only model inference is stubbed."""
        from source.enhancement import correction as dance
        import source.util as util_package
        root,fixture,poses,geometry=self.fixture()
        work=root/'work';work.mkdir();source=ex.local(fixture['record']['source'])
        fake=SimpleNamespace(SESSION=SimpleNamespace(object_ids=[1,2],detector_preview=None))
        def load_video(path,model):
            fake.SESSION.source=ex.local(path)
            fake.SESSION.object_ids=[7] if fake.SESSION.source.name=='foreground.mp4' else [1,2]
            fake.SESSION.selected_obj_id=fake.SESSION.object_ids[0]
            return 'CPU fixture'
        def select(id):fake.SESSION.selected_obj_id=id;return str(id)
        def track():
            directory=ex.local(fake.OUTPUT_DIR)/'track';directory.mkdir(parents=True)
            person=fixture['people'][str(fake.SESSION.selected_obj_id if fake.SESSION.selected_obj_id!=7 else 1)]
            s.sequence_view(person['masks'],directory/'masks',0,8)
            s.sequence_view(person['crop'],directory/'isolated_crop',0,8)
            ex.write_json(directory/'metadata.json',dict(source=s.relative(fake.SESSION.source),
                automatic_person_detection=dict(selected_obj_id=fake.SESSION.selected_obj_id,people=[])))
            fake.SESSION.result_dir=directory
        fake.load_video=load_video;fake.select_object=select;fake.track_video=track;fake.release_predictor=lambda:None
        fake.export_isolated_crop=lambda **kwargs:(fake.SESSION.result_dir/'isolated_crop',dict(x0=0,y0=0,x1=63,y1=63,w=64,h=64),'ok')
        workers=[]
        def worker(mode,cfg,**kwargs):
            workers.append(mode)
            self.assertEqual(cfg['gpu'],1)
            if mode=='pose':
                for person in cfg['people']:
                    for name in ('pose','driver_pose'):np.save(ex.local(person['directory'])/(name+'.npy'),poses)
            elif mode=='light':
                s.link(fixture['text'],cfg['text'])
                for person in cfg['people']:np.save(ex.local(person['latents']),np.zeros((8,4,64,64),np.float16))
            elif mode=='post':s.postprocess(cfg)
            else:self.fail('unexpected worker '+mode)
        removals=[]
        def inpaint(name,record,masks,**kwargs):
            self.assertEqual(name,'svor');self.assertEqual(kwargs['steps'],3)
            removals.append((record['selected_obj_id'],s.relative(masks)))
            folder=ex.unique_dir(root,'svor');s.link(record['source'],folder/'result.mp4')
            ex.write_json(folder/'experiment.json',dict(status='complete',experiment=record))
            return folder/'result.mp4'
        def geometry_track(*args):return geometry,ex.local(fixture['people']['1']['geometry'])
        def camera_track(*args,**kwargs):return s.arrays(fixture['camera']),ex.local(fixture['camera'])
        def scene_depth(*args):return np.load(ex.local(fixture['depth'])),ex.local(fixture['depth'])
        def correction(plan,*args,**kwargs):return dance.apply(plan,poses,poses)
        def anatomy(foreground,masks,record,**kwargs):
            cached=np.load(ex.local(record['prepared_background']['pose']))
            file=ex.local(foreground).parent/'poses.npz';np.savez(file,background=cached,foreground=poses)
            return cached,poses,file
        def relight(plan,gpu):
            self.assertIn('prepared_background',plan)
            folder=ex.local(plan['work']);s.sequence_view(plan['source_frames'],folder/'relit_frames',0,8)
            plan.update(status='selected',choice=quality.evaluate(plan,folder));ex.write_json(folder/'plan.json',plan)
            return plan
        unique_dir=ex.unique_dir
        def local_job(parent,label):
            if str(parent) in ('output/run_post','work/run_post'):parent=root/str(parent).replace('/','_')
            return unique_dir(parent,label)
        with contextlib.ExitStack() as stack:
            for patcher in (patch.object(s,'LIBRARY',s.relative(root/'library')),patch.object(s,'fingerprint',return_value='cpu-test'),
                patch.dict(sys.modules,{'source.util.pipeline':fake}),patch.object(util_package,'pipeline',fake,create=True),
                patch.object(s,'launch',side_effect=worker),patch.object(ex,'run_backend',side_effect=inpaint),
                patch.object(gp,'_track',side_effect=geometry_track),patch.object(scene_motion,'track',side_effect=camera_track),
                patch.object(gp.importlib,'reload',side_effect=lambda x:x),patch.object(depth,'scene_depth',side_effect=scene_depth),
                patch.object(dance,'run',side_effect=correction),patch.object(anatomical,'poses',side_effect=anatomy),
                patch.object(variants,'choose',side_effect=relight),patch.object(ex,'unique_dir',side_effect=local_job)):
                stack.enter_context(patcher)
            log=io.StringIO()
            with contextlib.redirect_stdout(log):
                destination=root/'chosen_output'
                s.precompute(dict(background=s.relative(source),work=s.relative(work),gpu=1,output_dir=s.relative(destination)))
                ready=s.prepared(source,deep=True)
                self.assertIsNotNone(ready)
                self.assertTrue(ex.local(ready['directory']).is_relative_to(destination))
                self.assertEqual(ex.read_json(destination/'current.json')['manifest'],ready['manifest'])
                self.assertEqual(set(ready['people']),{'1','2'})
                self.assertEqual(removals,[(id,ready['people'][str(id)]['removal_masks']) for id in (1,2)])
                self.assertNotEqual(ready['people']['1']['background'],ready['people']['2']['background'])
                for person in ready['people'].values():
                    self.assertTrue(ex.local(person['background']).is_file())
                # No online fallback to background inference is allowed.
                with patch.object(depth,'scene_depth',side_effect=AssertionError('online background depth')),patch.object(depth,'part_depths',side_effect=AssertionError('online background body depth')):
                    result=s.run_post(source,source,remove_id=1,gpu=1)
            (root/'orchestration.log').write_text(log.getvalue())
            self.assertEqual(result['status'],'complete')
            self.assertEqual(result['inputs']['driver_id'],2)
            self.assertEqual(result['inputs']['foreground_id'],7)
            self.assertEqual(result['inputs']['foreground_selection'],'automatic')
            self.assertGreater(result['total_seconds'],result['worker_seconds'])
            self.assertEqual(result['video']['frames'],8)
            self.assertTrue(ex.has_audio(result['final_video']))
            self.assertEqual(workers,['pose','light','post'])


if __name__=='__main__':unittest.main()

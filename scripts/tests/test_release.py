"""Check release safety boundaries, not Docker's implementation. Real lifecycle drills are separate."""
import contextlib
import io
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import release

class ReleaseSafetyTest(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup)
        self.private=patch.object(release,'PRIVATE',Path(self.temp.name)/'private');self.private.start();self.addCleanup(self.private.stop)
    def initialized(self):
        with contextlib.redirect_stdout(io.StringIO()):release.init('campus-test',18082)
        return release.load('campus-test')
    def test_init_never_overwrites_secrets_and_parent_prevents_host_access(self):
        self.initialized();p=release.folder('campus-test')/'secrets/JWT_SECRET';old=p.read_bytes()
        self.assertEqual(0o700,p.parent.stat().st_mode&0o777)
        self.assertEqual(0o600,(p.parent.parent/'state.json').stat().st_mode&0o777)
        with self.assertRaises(ValueError):release.init('campus-test',18082)
        self.assertEqual(old,p.read_bytes())
        for invalid in ('other-project','campus-../../production','campus-test;stop','campus-UPPER'):
            with self.assertRaises(ValueError):release.folder(invalid)
    def test_compose_is_scoped_and_contains_no_secret_values(self):
        state=self.initialized();credential=(release.folder('campus-test')/'secrets/JWT_SECRET').read_text()
        with patch.object(release,'run') as run:
            release.compose('campus-test',state,['down'])
        args=run.call_args.args[0];env=run.call_args.kwargs['env']
        self.assertIn('-p',args);self.assertIn('campus-test',args);self.assertNotIn('-v',args)
        self.assertNotIn(credential,json.dumps([args,env]));self.assertIn('/dev/null',args)
    def test_failed_compatible_rollout_restores_previous_id_and_keeps_state(self):
        state=self.initialized();old='sha256:'+'a'*64;new='sha256:'+'b'*64
        state.update(currentImage=old,status='HEALTHY');release.save(release.folder('campus-test')/'state.json',state)
        failure=subprocess.CalledProcessError(1,['docker','compose'])
        with patch.object(release,'capture',return_value=new),patch.object(release,'backup') as backup,patch.object(release,'compose',side_effect=[failure,None]) as compose,contextlib.redirect_stdout(io.StringIO()):
            with self.assertRaises(subprocess.CalledProcessError):release.deploy('campus-test','candidate',True)
        backup.assert_called_once();self.assertEqual(old,compose.call_args_list[1].args[1]['currentImage'])
        self.assertEqual(state,release.load('campus-test'));self.assertFalse((release.folder('campus-test')/'pending.json').exists())
    def test_upgrade_without_reviewed_schema_compatibility_does_not_mutate(self):
        state=self.initialized();state['currentImage']='sha256:'+'a'*64;release.save(release.folder('campus-test')/'state.json',state)
        with patch.object(release,'capture',return_value='sha256:'+'b'*64),patch.object(release,'compose') as compose,patch.object(release,'backup') as backup:
            with self.assertRaises(ValueError):release.deploy('campus-test','candidate')
        compose.assert_not_called();backup.assert_not_called();self.assertEqual(state,release.load('campus-test'))
    def test_restore_refuses_nonempty_database_before_import(self):
        state=self.initialized();p=Path(self.temp.name)/'backup.sql';p.write_text('-- synthetic')
        with patch.object(release,'compose'),patch.object(release,'container',return_value='scoped-id'),patch.object(release,'capture',return_value='1'),patch.object(release,'run') as run:
            with self.assertRaises(ValueError):release.restore('campus-test',state,p)
        run.assert_not_called()

if __name__=='__main__':unittest.main()

import { describe, it, expect } from '@ohos/hypium';
import { abilityDelegatorRegistry } from '@kit.TestKit';
import { window } from '@kit.ArkUI';

function pause(ms: number): Promise<void> { return new Promise<void>((resolve) => { setTimeout(resolve,ms); }); }
export default function layoutReview(): void {
  describe('LayoutReview', () => {
    it('realWindowRotatesAndLoginRemainsReachable',0,async () => {
      const delegator = abilityDelegatorRegistry.getAbilityDelegator();
      await delegator.startAbility({ bundleName: 'com.pbpromax.campus', abilityName: 'EntryAbility' });
      await pause(700);
      const ability = await delegator.getCurrentTopAbility();
      const main = await window.getLastWindow(ability.context);
      try {
        await main.setPreferredOrientation(window.Orientation.PORTRAIT); await pause(600);
        const before = main.getWindowProperties().windowRect;
        expect(before.height > before.width).assertTrue();
        const router = main.getUIContext().getRouter();
        await router.pushUrl({ url: 'pages/Needs' }); await pause(400);
        await main.setPreferredOrientation(window.Orientation.LANDSCAPE); await pause(1000);
        const after = main.getWindowProperties().windowRect;
        expect(after.width > after.height).assertTrue();
        expect(router.getState().name.includes('Needs')).assertTrue();
        const shot = await delegator.executeShellCommand('uitest screenCap -p /data/local/tmp/campus-step6-landscape.png',5);
        expect(shot.exitCode).assertEqual(0);
      } finally { await main.setPreferredOrientation(window.Orientation.PORTRAIT); await pause(600); }
    });
  });
}

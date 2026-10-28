import { Palette } from '../ui/Presentation';
import { http } from '@kit.NetworkKit';
import { hilog } from '@kit.PerformanceAnalysisKit';
import { router } from '@kit.ArkUI';

interface DemandSummary {
  id: number;
  title: string;
}

interface DemandListResponse {
  code: number;
  data: DemandSummary[];
}

@Entry
@Component
struct Index {
  @State private status: string = '正在检查后端连接…';
  @State private checking: boolean = false;

  aboutToAppear(): void {
    this.checkBackend();
  }

  private async checkBackend(): Promise<void> {
    if (this.checking) {
      return;
    }
    this.checking = true;
    this.status = '正在检查后端连接…';
    const request = http.createHttp();
    try {
      const response = await request.request('http://127.0.0.1:8081/api/demands', {
        method: http.RequestMethod.GET,
        expectDataType: http.HttpDataType.STRING,
        connectTimeout: 5000,
        readTimeout: 5000
      });
      if (response.responseCode !== 200) {
        throw new Error('Unexpected HTTP response');
      }
      const body = JSON.parse(response.result as string) as DemandListResponse;
      if (body.code !== 200 || !Array.isArray(body.data)) {
        throw new Error('Invalid backend response');
      }
      this.status = '';
      hilog.info(0x0000, 'CampusEnv', 'BACKEND_OK HTTP=200 count=%{public}d', body.data.length);
    } catch (error) {
      this.status = '暂时连接不上，稍后再试试。';
      hilog.error(0x0000, 'CampusEnv', 'BACKEND_FAILED');
    } finally {
      request.destroy();
      this.checking = false;
    }
  }

  build() {
    Column() {
      Row({ space: 6 }) { Text('✦').fontSize(23).fontColor(Palette.green); Text('campus').fontSize(24).fontWeight(FontWeight.Bold).letterSpacing(-0.7).fontColor(Palette.ink); Blank(); Text('校园互助').fontSize(12).fontColor(Palette.muted) }.width('100%')
      Blank().layoutWeight(1)
      Stack() {
        Circle().width(204).height(204).fill('#DFEACD')
        Text('↗').fontSize(114).fontWeight(FontWeight.Medium).fontColor(Palette.green).rotate({ angle: -8 })
        Row({ space: 8 }) { Text('✦').fontSize(17).fontColor(Palette.green); Text('小事，也有人在意').fontSize(12).fontColor(Palette.ink) }
          .padding({ left: 16, right: 16, top: 12, bottom: 12 }).backgroundColor('#FFFFFF').borderRadius(14).translate({ x: 40, y: 74 })
      }.width('100%').height(238)
      Blank().height(28)
      Text('需要帮忙的时候，\n总有人在。').fontSize(33).lineHeight(45).fontWeight(FontWeight.Bold).fontColor(Palette.ink).width('100%')
      Text('借件东西、顺路跑腿，或弄懂一道题。\n从一句话开始，让互助更简单。').fontSize(14).lineHeight(25).fontColor(Palette.muted).width('100%').margin({ top: 14 })
      Blank().layoutWeight(1)
      Button('开始使用  ↗').height(52).width('100%').fontSize(16).fontWeight(FontWeight.Medium).borderRadius(15).backgroundColor(Palette.green).id('enter-campus')
        .onClick(() => { router.pushUrl({ url: 'pages/Needs' }); })
      if (this.status.length > 0 && !this.checking) {
        Text(this.status + '  重新连接').fontSize(11).fontColor(Palette.muted).padding(10).onClick(() => { this.checkBackend(); })
      } else { Text('一句话说需求，一起把事情做好').fontSize(11).fontColor(Palette.muted).padding(14) }
    }.width('100%').height('100%').padding({ left: 27, right: 27, top: 24, bottom: 15 }).backgroundColor(Palette.canvas)
  }
}

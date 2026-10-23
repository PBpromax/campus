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
      this.status = `连接成功：已读取任务列表（${body.data.length} 条）`;
      hilog.info(0x0000, 'CampusEnv', 'BACKEND_OK HTTP=200 count=%{public}d', body.data.length);
    } catch (error) {
      this.status = '连接失败：请检查后端8081及hdc端口转发，再点重试';
      hilog.error(0x0000, 'CampusEnv', 'BACKEND_FAILED');
    } finally {
      request.destroy();
      this.checking = false;
    }
  }

  build() {
    Column({ space: 16 }) {
      Text('Campus')
        .fontSize(32)
        .fontWeight(FontWeight.Bold)
      Text('M2 · 鸿蒙客户端')
        .fontSize(20)
      Text('跑腿代办 · 学习辅导 · 物品借用 · 其他互助')
        .fontSize(14)
      Button('进入统一需求与草稿')
        .onClick(() => { router.pushUrl({ url: 'pages/Needs' }); })
      Text('开发环境验证：应用已启动')
        .fontSize(16)
      Text(this.status)
        .fontSize(16)
        .textAlign(TextAlign.Center)
      Button(this.checking ? '检查中…' : '重新检查后端连接')
        .enabled(!this.checking)
        .onClick(() => {
          this.checkBackend();
        })
      Text('仅查询本地开发后端，不发布任务')
        .fontSize(14)
        .fontColor('#666666')
    }
    .width('100%')
    .height('100%')
    .justifyContent(FlexAlign.Center)
    .padding(24)
  }
}

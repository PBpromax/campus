import { AiStreamDecoder, AiEvent } from './AiStream';
import { http } from '@kit.NetworkKit';
import { BusinessError } from '@kit.BasicServicesKit';
import { ApiEnvelope, LoginRequest, LoginResult } from '../domain/NeedModels';

export class NeedApi {
  // Development emulator only; hdc rport routes device localhost to the Mac backend.
  private readonly baseUrl: string = 'http://127.0.0.1:8081/api';
  private token: string = '';
  private userId: number = 0;
  currentUserId(): number { return this.userId; }

  private streaming: http.HttpRequest | undefined = undefined;
  private cancelCurrent: (() => void) | undefined = undefined;
  cancelStream(): void { if (this.cancelCurrent !== undefined) { this.cancelCurrent(); this.cancelCurrent = undefined; } if (this.streaming !== undefined) { this.streaming.destroy(); this.streaming = undefined; } }
  clear(): void { this.cancelStream(); this.token = ''; this.userId = 0; }

  async login(username: string, password: string): Promise<void> {
    const body: LoginRequest = { username: username, password: password };
    const result = await this.send<LoginResult>('/users/login', http.RequestMethod.POST, JSON.stringify(body));
    this.token = result.token; // Memory only. Password and JWT are never saved to preferences or logs.
    this.userId = result.user.id;
  }

  async stream(path: string, body: string, emit: (event: AiEvent) => void): Promise<void> {
    const request = http.createHttp(); this.streaming = request;
    const decoder = new AiStreamDecoder();
    try {
      await new Promise<void>((resolve, reject) => {
        let ended = false; let status = 0; let settled = false;
        const timer = setTimeout(() => { fail('回复超时，请重新加载核对；未自动重试'); }, 70000);
        const fail = (message: string): void => {
          if (settled) { return; } settled = true; clearTimeout(timer); request.destroy(); reject(new Error(message));
        };
        this.cancelCurrent = (): void => { fail('已停止接收回复，请重新加载核对已保存消息'); };
        const finish = (): void => {
          if (!ended || status === 0 || settled) { return; }
          if (status !== 200) { fail('对话请求失败（HTTP ' + status + '），请检查登录和模型配置'); return; }
          try { decoder.finish(); settled = true; clearTimeout(timer); resolve(); }
          catch (error) { fail((error as BusinessError).message); }
        };
        request.on('dataReceive', (bytes: ArrayBuffer) => {
          if (settled) { return; }
          try { decoder.feed(bytes, emit); } catch (error) { fail((error as BusinessError).message); }
        });
        request.on('dataEnd', () => { ended = true; finish(); });
        const headers: Record<string,string> = { 'Content-Type': 'application/json', 'Accept': 'text/event-stream', 'Authorization': 'Bearer ' + this.token };
        request.requestInStream(this.baseUrl + path, { method: http.RequestMethod.POST, header: headers, extraData: body, connectTimeout: 5000, readTimeout: 65000 })
          .then((code: number) => { status = code; if (code === 401) { this.token = ''; this.userId = 0; } finish(); })
          .catch((_error: BusinessError) => { fail('连接中断或已停止，请重新加载核对；输入保留'); });
      });
    } finally { if (this.streaming === request) { this.streaming = undefined; this.cancelCurrent = undefined; } request.destroy(); }
  }

  async send<T>(path: string, method: http.RequestMethod = http.RequestMethod.GET, body: string = ''): Promise<T> {
    const request = http.createHttp();
    const headers: Record<string, string> = { 'Content-Type': 'application/json' };
    if (this.token.length > 0) { headers['Authorization'] = 'Bearer ' + this.token; }
    try {
      const options: http.HttpRequestOptions = {
        method: method, header: headers,
        expectDataType: http.HttpDataType.STRING, connectTimeout: 5000, readTimeout: 10000
      };
      if (body.length > 0) { options.extraData = body; }
      const response = await request.request(this.baseUrl + path, options);
      // A trusted action can legitimately return 403 (own task / credit restriction).
      // Such a business rejection must not silently discard a still-valid login.
      if (response.responseCode === 401) {
        this.clear();
        throw new Error('登录失效，请退出并重新登录');
      }
      const data = JSON.parse(response.result as string) as ApiEnvelope<T>;
      if (response.responseCode !== 200 || data.code !== 200) {
        throw new Error(data.message || '请求失败，请检查连接或重新加载');
      }
      return data.data;
    } catch (error) {
      const detail = error as BusinessError;
      throw new Error(path + '：' + detail.message);
    } finally {
      request.destroy();
    }
  }
}

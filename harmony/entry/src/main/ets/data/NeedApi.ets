import { http } from '@kit.NetworkKit';
import { BusinessError } from '@kit.BasicServicesKit';
import { ApiEnvelope, LoginRequest, LoginResult } from '../domain/NeedModels';

export class NeedApi {
  // Development emulator only; hdc rport routes device localhost to the Mac backend.
  private readonly baseUrl: string = 'http://127.0.0.1:8081/api';
  private token: string = '';

  clear(): void { this.token = ''; }

  async login(username: string, password: string): Promise<void> {
    const body: LoginRequest = { username: username, password: password };
    const result = await this.send<LoginResult>('/users/login', http.RequestMethod.POST, JSON.stringify(body));
    this.token = result.token; // Memory only. Password and JWT are never saved to preferences or logs.
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
      if (response.responseCode === 401 || response.responseCode === 403) {
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

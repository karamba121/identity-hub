import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';

export interface RegistrationRequest {
  displayName: string;
  email: string;
  password: string;
}

export interface RegistrationAccepted {
  message: string;
}

@Injectable({ providedIn: 'root' })
export class RegistrationApiService {
  constructor(private readonly http: HttpClient) {}

  prepareCsrf(): Observable<void> {
    return this.http.get('/api/v1/registrations/csrf').pipe(map(() => undefined));
  }

  register(request: RegistrationRequest): Observable<RegistrationAccepted> {
    return this.http.post<RegistrationAccepted>('/api/v1/registrations', request);
  }

  verify(token: string): Observable<void> {
    return this.http.post<void>('/api/v1/registrations/verify', { token });
  }
}

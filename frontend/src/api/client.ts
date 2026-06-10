import type {
  ServiceDTO,
  RegisterServiceRequest,
  DependencyDTO,
  AddDependencyRequest,
  TraversalResultDTO,
} from '../types';

const BASE = '/api/v1';

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE}${path}`, {
    headers: { 'Content-Type': 'application/json', ...init?.headers },
    ...init,
  });
  if (!res.ok) {
    const body = await res.json().catch(() => ({ message: res.statusText }));
    throw new Error(body.message ?? res.statusText);
  }
  if (res.status === 204) return undefined as unknown as T;
  return res.json() as Promise<T>;
}

export const api = {
  listServices: () =>
    request<ServiceDTO[]>('/services'),

  registerService: (body: RegisterServiceRequest) =>
    request<ServiceDTO>('/services', {
      method: 'POST',
      body: JSON.stringify(body),
    }),

  deleteService: (name: string) =>
    request<void>(`/services/${encodeURIComponent(name)}`, { method: 'DELETE' }),

  getDependencies: (name: string) =>
    request<DependencyDTO[]>(`/services/${encodeURIComponent(name)}/dependencies`),

  addDependency: (name: string, body: AddDependencyRequest) =>
    request<DependencyDTO>(`/services/${encodeURIComponent(name)}/dependencies`, {
      method: 'POST',
      body: JSON.stringify(body),
    }),

  removeDependency: (name: string, depName: string) =>
    request<void>(
      `/services/${encodeURIComponent(name)}/dependencies/${encodeURIComponent(depName)}`,
      { method: 'DELETE' },
    ),

  getDownstream: (name: string) =>
    request<TraversalResultDTO>(`/services/${encodeURIComponent(name)}/downstream`),

  getUpstream: (name: string) =>
    request<TraversalResultDTO>(`/services/${encodeURIComponent(name)}/upstream`),
};

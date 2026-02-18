import axios, { AxiosInstance } from 'axios';
import {
  AuthResponse,
  LoginRequest,
  SignupRequest,
  Project,
  ProjectCreateRequest,
  ProjectUpdateRequest,
  Deployment,
  DeployRequest,
} from '../types';

const API_BASE: string = process.env.REACT_APP_API_URL || '';

const api: AxiosInstance = axios.create({
  baseURL: API_BASE,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Attach JWT token to requests
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Handle 401 (expired/invalid token)
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token');
      localStorage.removeItem('user');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

// ===== AUTH =====
export const authAPI = {
  login: (data: LoginRequest) =>
    api.post<AuthResponse>('/api/auth/login', data),

  signup: (data: SignupRequest) =>
    api.post<AuthResponse>('/api/auth/signup', data),
};

// ===== PROJECTS =====
export const projectAPI = {
  list: () =>
    api.get<Project[]>('/api/projects'),

  get: (id: string) =>
    api.get<Project>(`/api/projects/${id}`),

  create: (data: ProjectCreateRequest) =>
    api.post<Project>('/api/projects', data),

  update: (id: string, data: ProjectUpdateRequest) =>
    api.put<Project>(`/api/projects/${id}`, data),

  delete: (id: string) =>
    api.delete(`/api/projects/${id}`),
};

// ===== DEPLOYMENTS =====
export const deployAPI = {
  create: (data: DeployRequest) =>
    api.post<Deployment>('/api/deploy', data),

  get: (id: string) =>
    api.get<Deployment>(`/api/deployments/${id}`),

  listByProject: (projectId: string) =>
    api.get<Deployment[]>(`/api/deployments/project/${projectId}`),
};

export default api;

import axios from 'axios';

export const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

const api = axios.create({
  baseURL: API_BASE,
  headers: { 'Content-Type': 'application/json' },
});

// Attach JWT token to every request
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('accessToken');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Handle 401 — clear token and redirect to login
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('accessToken');
      localStorage.removeItem('refreshToken');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

export default api;

// ==================== Auth API ====================
export const authApi = {
  register: (data) => api.post('/api/auth/register', data),
  login: (data) => api.post('/api/auth/login', data),
  getProfile: () => api.get('/api/auth/me'),
  verifyEmail: (data) => api.post('/api/auth/verify-email', data),
  deleteAccount: (password) => api.post('/api/auth/delete', { password }),
  updateProfile: (data) => api.put('/api/auth/me', data),
  uploadProfilePhoto: (formData) => api.post('/api/auth/me/photo', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  }),
};

// ==================== Check-In API ====================
export const checkinApi = {
  perform: () => api.post('/api/checkin'),
  updateSettings: (data) => api.put('/api/settings/checkin', data),
  pauseSwitch: () => api.post('/api/switch/pause'),
  resumeSwitch: () => api.post('/api/switch/resume'),
};

// ==================== Vault API ====================
export const vaultApi = {
  list: () => api.get('/api/vault/items'),
  get: (id, password) => api.get(`/api/vault/items/${id}`, {
    headers: { 'X-Vault-Password': password }
  }),
  create: (formData, password) => api.post('/api/vault/items', formData, {
    headers: { 
      'X-Vault-Password': password,
      'Content-Type': 'multipart/form-data'
    }
  }),
  update: (id, payload, password) => api.put(`/api/vault/items/${id}`, payload, {
    headers: { 'X-Vault-Password': password }
  }),
  download: (id, password) => api.get(`/api/vault/items/${id}/download`, {
    headers: { 'X-Vault-Password': password },
    responseType: 'blob'
  }),
  delete: (id, password) => api.delete(`/api/vault/items/${id}`, {
    headers: { 'X-Vault-Password': password }
  }),
};

// ==================== Recipients API ====================
export const recipientApi = {
  list: () => api.get('/api/recipients'),
  create: (data) => api.post('/api/recipients', data),
  update: (id, data) => api.put(`/api/recipients/${id}`, data),
  delete: (id) => api.delete(`/api/recipients/${id}`),
  assignItems: (id, vaultItemIds) => api.put(`/api/recipients/${id}/items`, { vaultItemIds }),
};

// ==================== Audit API ====================
export const auditApi = {
  getLogs: (page = 0, size = 20) => api.get(`/api/audit/logs?page=${page}&size=${size}`),
};


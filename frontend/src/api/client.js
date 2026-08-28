import axios from 'axios';

// Support both VITE_API_BASE_URL and VITE_API_URL (legacy) for compatibility
export const API_BASE = import.meta.env.VITE_API_BASE_URL || import.meta.env.VITE_API_URL?.replace('/api', '') || 'http://localhost:8080';

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

// Handle 401 — clear token and redirect to login.
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
  forgotPassword: (email) => api.post('/api/auth/forgot-password', { email }),
  resetPassword: (token, newPassword) => api.post('/api/auth/reset-password', { token, newPassword }),
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
// Zero-knowledge design:
//   - All encryption/decryption happens in the browser (see src/crypto/vault.js)
//   - The vault password NEVER leaves the browser
//   - The server receives and stores only ciphertext, IVs, encrypted DEKs, and salts
//   - No X-Vault-Password header — the server has no role in decryption

export const vaultApi = {
  /** Returns a list of vault items with metadata only (no ciphertext — listed separately) */
  list: () => api.get('/api/vault/items'),

  /**
   * Creates a vault item. All encryption is performed by the caller before this is called.
   *
   * @param {Object} encryptedPayload
   *   {string} label
   *   {string} contentType
   *   {string|null} ciphertext       - Base64 AES-256-GCM encrypted content
   *   {string|null} iv               - Base64 GCM IV for content
   *   {string} encryptedDEK          - Base64 DEK wrapped with user master key
   *   {string} dekIv                 - Base64 IV used during DEK wrapping
   *   {string} salt                  - Base64 Argon2id salt
   *   {string} rawDEK                - Base64 raw DEK bytes (used server-side for release path wrap only)
   *   {string|null} fileCiphertext   - Base64 AES-256-GCM encrypted file bytes (if file attached)
   *   {string|null} fileIv           - Base64 GCM IV for file
   *   {string|null} originalFileName - Original file name (for download)
   *   {string[]} recipientIds        - UUIDs of assigned recipients
   */
  create: (encryptedPayload) => api.post('/api/vault/items', encryptedPayload),

  /**
   * Returns the encrypted blob for a single vault item (ciphertext, iv, encryptedDEK, dekIv, salt).
   * Decryption is performed by the caller using vault.js#decryptContent.
   */
  get: (id) => api.get(`/api/vault/items/${id}`),

  /**
   * Updates a vault item. The caller must re-encrypt the new content before calling this.
   * The existing DEK is re-used (no re-key needed for content updates).
   */
  update: (id, encryptedPayload) => api.put(`/api/vault/items/${id}`, encryptedPayload),

  /**
   * Downloads the encrypted file blob for a vault item.
   * The caller decrypts the returned blob using vault.js#decryptFile.
   */
  download: (id) => api.get(`/api/vault/items/${id}/download`, { responseType: 'json' }),

  /** Soft-deletes a vault item. Password verification is done client-side before calling. */
  delete: (id) => api.delete(`/api/vault/items/${id}`),
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

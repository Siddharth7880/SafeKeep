import { useForm } from 'react-hook-form';
import { Link, useNavigate } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import { authApi } from '../api/client';
import { useAuthStore } from '../store/authStore';
import toast from 'react-hot-toast';
import { Shield, Eye, EyeOff, AlertTriangle, X } from 'lucide-react';
import { useState } from 'react';
import './AuthPage.css';

export default function LoginPage() {
  const navigate = useNavigate();
  const setAuth = useAuthStore((s) => s.setAuth);
  const [showPassword, setShowPassword] = useState(false);
  const [loginError, setLoginError] = useState(null);

  const { register, handleSubmit, formState: { errors }, watch } = useForm();

  // Clear error whenever the user types anything
  watch(() => { if (loginError) setLoginError(null); });

  const loginMutation = useMutation({
    mutationFn: authApi.login,
    onSuccess: (res) => {
      const { data } = res.data;
      setLoginError(null);
      setAuth(data.user, data.accessToken, data.refreshToken);
      toast.success('Welcome back!');
      navigate('/dashboard');
    },
    onError: (err) => {
      const msg = err.response?.data?.message || 'Invalid email or password. Please try again.';
      setLoginError(msg);
    },
  });

  const onSubmit = (data) => {
    setLoginError(null);
    loginMutation.mutate(data);
  };

  return (
    <div className="auth-page">
      {/* Left side: Form */}
      <div className="auth-left">
        <div className="auth-card">
          <div className="auth-logo">
            <Shield size={32} className="logo-icon" />
            <span>SafeKeep</span>
          </div>
          <h2 style={{ marginBottom: 4 }}>Welcome back</h2>
          <p className="text-muted text-sm" style={{ marginBottom: loginError ? 16 : 24 }}>
            Sign in to access your vault
          </p>

          {/* ---- Inline Error Alert ---- */}
          {loginError && (
            <div className="auth-error-alert" role="alert">
              <div className="auth-error-icon">
                <AlertTriangle size={18} />
              </div>
              <div className="auth-error-text">
                <strong>Login Failed</strong>
                <span>{loginError}</span>
              </div>
              <button
                type="button"
                className="auth-error-close"
                onClick={() => setLoginError(null)}
                aria-label="Dismiss"
              >
                <X size={14} />
              </button>
            </div>
          )}

          <form onSubmit={handleSubmit(onSubmit)} className="auth-form">
            <div className="form-group">
              <label className="form-label">Email Address</label>
              <input
                id="email"
                className={`form-input${loginError ? ' input-error' : ''}`}
                type="email"
                placeholder="you@example.com"
                {...register('email', { required: 'Email is required' })}
              />
              {errors.email && <span className="form-error">{errors.email.message}</span>}
            </div>

            <div className="form-group">
              <label className="form-label">Password</label>
              <div className="input-with-icon">
                <input
                  id="password"
                  className={`form-input${loginError ? ' input-error' : ''}`}
                  type={showPassword ? 'text' : 'password'}
                  placeholder="Your password"
                  {...register('password', { required: 'Password is required' })}
                />
                <button type="button" className="input-icon-btn" onClick={() => setShowPassword(!showPassword)}>
                  {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                </button>
              </div>
              {errors.password && <span className="form-error">{errors.password.message}</span>}
            </div>

            <button
              id="login-submit"
              type="submit"
              className="btn btn-primary w-full btn-lg"
              style={{ marginTop: 8 }}
              disabled={loginMutation.isPending}
            >
              {loginMutation.isPending ? <span className="spinner" /> : 'Sign In'}
            </button>
          </form>

          <div className="auth-security-badges">
            <div className="auth-security-badge">
              <Shield size={14} /> End-to-end encrypted storage
            </div>
          </div>

          <p className="auth-footer">
            Don't have an account? <Link to="/register">Create one</Link>
          </p>
        </div>
      </div>

      {/* Right side: Atmosphere & Branding */}
      <div className="auth-right">
        <div className="auth-right-bg" />
        <div className="auth-right-grid" />
        <div className="auth-right-content">
          <div className="auth-illustration">
            <Shield size={48} />
          </div>
          <div className="auth-quote-container">
            <h3 className="auth-quote">"Security is not a product, but a process. Protect your digital legacy."</h3>
            <span className="auth-quote-author">SafeKeep Security</span>
          </div>
        </div>
      </div>
    </div>
  );
}

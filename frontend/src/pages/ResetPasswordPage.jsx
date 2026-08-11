import { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { useMutation } from '@tanstack/react-query';
import { authApi } from '../api/client';
import toast from 'react-hot-toast';
import { Shield, Eye, EyeOff, CheckCircle, AlertTriangle, X, Lock } from 'lucide-react';
import './AuthPage.css';

const passwordChecks = (value) => ({
  length: value?.length >= 8,
  uppercase: /[A-Z]/.test(value || ''),
  number: /\d/.test(value || ''),
});

export default function ResetPasswordPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token') || '';
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(false);

  const { register, handleSubmit, watch, formState: { errors } } = useForm();
  const watchedPassword = watch('newPassword', '');
  const checks = passwordChecks(watchedPassword);
  const allPassed = Object.values(checks).every(Boolean);

  watch(() => { if (error) setError(null); });

  const mutation = useMutation({
    mutationFn: (data) => authApi.resetPassword(token, data.newPassword),
    onSuccess: () => {
      setSuccess(true);
      toast.success('Password reset! Redirecting to login…');
      setTimeout(() => navigate('/login'), 2500);
    },
    onError: (err) => {
      const msg = err.response?.data?.message || 'This reset link is invalid or has expired.';
      setError(msg);
    },
  });

  if (!token) {
    return (
      <div className="auth-page">
        <div className="auth-left">
          <div className="auth-card animate-scale-in" style={{ textAlign: 'center' }}>
            <div style={{ width: 64, height: 64, borderRadius: '50%', background: 'rgba(239,68,68,0.1)', border: '1px solid rgba(239,68,68,0.3)', display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto 20px' }}>
              <AlertTriangle size={30} color="var(--danger)" />
            </div>
            <h2 style={{ marginBottom: 12 }}>Invalid Link</h2>
            <p className="text-muted text-sm" style={{ marginBottom: 24 }}>
              This password reset link is missing a token. Please request a new reset link.
            </p>
            <Link to="/forgot-password" className="btn btn-primary w-full" style={{ justifyContent: 'center' }}>Request New Link</Link>
          </div>
        </div>
        <div className="auth-right">
          <div className="auth-right-bg" />
          <div className="auth-right-grid" />
          <div className="auth-right-content">
            <div className="auth-illustration"><Shield size={48} /></div>
          </div>
        </div>
      </div>
    );
  }

  if (success) {
    return (
      <div className="auth-page">
        <div className="auth-left">
          <div className="auth-card animate-scale-in" style={{ textAlign: 'center' }}>
            <div style={{ width: 72, height: 72, borderRadius: '50%', background: 'rgba(16,185,129,0.1)', border: '1px solid rgba(16,185,129,0.3)', display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto 24px' }}>
              <CheckCircle size={36} color="var(--success)" />
            </div>
            <h2 style={{ marginBottom: 12 }}>Password Reset!</h2>
            <p className="text-muted text-sm">Your password has been updated successfully. Redirecting you to login…</p>
            <div style={{ marginTop: 24 }}><span className="spinner" /></div>
          </div>
        </div>
        <div className="auth-right">
          <div className="auth-right-bg" />
          <div className="auth-right-grid" />
        </div>
      </div>
    );
  }

  return (
    <div className="auth-page">
      <div className="auth-left">
        <div className="auth-card">
          <div className="auth-logo">
            <Shield size={32} className="logo-icon" />
            <span>SafeKeep</span>
          </div>

          <div style={{ marginBottom: 24 }}>
            <h2 style={{ marginBottom: 8 }}>Create New Password</h2>
            <p className="text-muted text-sm">
              Choose a strong password you haven't used before.
            </p>
          </div>

          {error && (
            <div className="auth-error-alert" role="alert" style={{ marginBottom: 20 }}>
              <div className="auth-error-icon"><AlertTriangle size={18} /></div>
              <div className="auth-error-text">
                <strong>Reset Failed</strong>
                <span>{error}</span>
              </div>
              <button type="button" className="auth-error-close" onClick={() => setError(null)}>
                <X size={14} />
              </button>
            </div>
          )}

          <form onSubmit={handleSubmit((d) => mutation.mutate(d))} className="auth-form">
            <div className="form-group">
              <label className="form-label">New Password</label>
              <div className="input-with-icon">
                <input
                  id="new-password"
                  className="form-input"
                  type={showPassword ? 'text' : 'password'}
                  placeholder="Min. 8 characters"
                  {...register('newPassword', {
                    required: 'Password is required',
                    minLength: { value: 8, message: 'At least 8 characters' },
                  })}
                />
                <button type="button" className="input-icon-btn" onClick={() => setShowPassword(!showPassword)}>
                  {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                </button>
              </div>
              {errors.newPassword && <span className="form-error">{errors.newPassword.message}</span>}

              {/* Password strength indicators */}
              {watchedPassword && (
                <div className="password-checks">
                  <span className={`password-check ${checks.length ? 'check-pass' : 'check-fail'}`}>
                    {checks.length ? '✓' : '○'} 8+ chars
                  </span>
                  <span className={`password-check ${checks.uppercase ? 'check-pass' : 'check-fail'}`}>
                    {checks.uppercase ? '✓' : '○'} Uppercase
                  </span>
                  <span className={`password-check ${checks.number ? 'check-pass' : 'check-fail'}`}>
                    {checks.number ? '✓' : '○'} Number
                  </span>
                </div>
              )}
            </div>

            <div className="form-group">
              <label className="form-label">Confirm New Password</label>
              <div className="input-with-icon">
                <input
                  id="confirm-password"
                  className="form-input"
                  type={showConfirm ? 'text' : 'password'}
                  placeholder="Repeat password"
                  {...register('confirmPassword', {
                    required: 'Please confirm your password',
                    validate: (v) => v === watchedPassword || 'Passwords do not match',
                  })}
                />
                <button type="button" className="input-icon-btn" onClick={() => setShowConfirm(!showConfirm)}>
                  {showConfirm ? <EyeOff size={16} /> : <Eye size={16} />}
                </button>
              </div>
              {errors.confirmPassword && <span className="form-error">{errors.confirmPassword.message}</span>}
            </div>

            <button
              id="reset-submit"
              type="submit"
              className="btn btn-primary w-full btn-lg"
              style={{ marginTop: 8 }}
              disabled={mutation.isPending || !allPassed}
            >
              {mutation.isPending ? <span className="spinner" /> : <><Lock size={16} /> Reset Password</>}
            </button>
          </form>

          <p className="auth-footer" style={{ marginTop: 20 }}>
            Remember your password? <Link to="/login">Sign In</Link>
          </p>
        </div>
      </div>

      <div className="auth-right">
        <div className="auth-right-bg" />
        <div className="auth-right-grid" />
        <div className="auth-right-content">
          <div className="auth-illustration"><Shield size={48} /></div>
          <div className="auth-quote-container">
            <h3 className="auth-quote">"Security is not a product, but a process. Protect your digital legacy."</h3>
            <span className="auth-quote-author">SafeKeep Security</span>
          </div>
        </div>
      </div>
    </div>
  );
}

import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { useMutation } from '@tanstack/react-query';
import { authApi } from '../api/client';
import toast from 'react-hot-toast';
import { Shield, Mail, ArrowLeft, CheckCircle, AlertTriangle, X } from 'lucide-react';
import './AuthPage.css';

export default function ForgotPasswordPage() {
  const [submitted, setSubmitted] = useState(false);
  const [error, setError] = useState(null);

  const { register, handleSubmit, formState: { errors }, watch } = useForm();

  watch(() => { if (error) setError(null); });

  const mutation = useMutation({
    mutationFn: (data) => authApi.forgotPassword(data.email),
    onSuccess: () => {
      setSubmitted(true);
      setError(null);
    },
    onError: (err) => {
      const msg = err.response?.data?.message || 'Something went wrong. Please try again.';
      setError(msg);
    },
  });

  if (submitted) {
    return (
      <div className="auth-page">
        <div className="auth-left">
          <div className="auth-card animate-scale-in" style={{ textAlign: 'center' }}>
            <div style={{
              width: 72, height: 72, borderRadius: '50%', background: 'rgba(16,185,129,0.1)',
              border: '1px solid rgba(16,185,129,0.3)', display: 'flex', alignItems: 'center',
              justifyContent: 'center', margin: '0 auto 24px',
            }}>
              <CheckCircle size={36} color="var(--success)" />
            </div>
            <h2 style={{ marginBottom: 12 }}>Check Your Email</h2>
            <p className="text-muted text-sm" style={{ marginBottom: 32, lineHeight: 1.8 }}>
              If an account exists for the email you entered, we've sent a password reset link. 
              The link will expire in <strong style={{ color: 'var(--text-primary)' }}>30 minutes</strong>.
            </p>
            <p className="text-muted text-sm" style={{ marginBottom: 24 }}>
              Didn't receive it? Check your spam folder or try again.
            </p>
            <Link to="/login" className="btn btn-ghost w-full" style={{ justifyContent: 'center' }}>
              <ArrowLeft size={16} /> Back to Login
            </Link>
          </div>
        </div>
        <div className="auth-right">
          <div className="auth-right-bg" />
          <div className="auth-right-grid" />
          <div className="auth-right-content">
            <div className="auth-illustration"><Shield size={48} /></div>
            <div className="auth-quote-container">
              <h3 className="auth-quote">"Security is not a product, but a process."</h3>
              <span className="auth-quote-author">SafeKeep Security</span>
            </div>
          </div>
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

          <div style={{ marginBottom: 28 }}>
            <h2 style={{ marginBottom: 8 }}>Reset Password</h2>
            <p className="text-muted text-sm">
              Enter your account email and we'll send you a secure link to reset your password.
            </p>
          </div>

          {error && (
            <div className="auth-error-alert" role="alert" style={{ marginBottom: 20 }}>
              <div className="auth-error-icon"><AlertTriangle size={18} /></div>
              <div className="auth-error-text">
                <strong>Error</strong>
                <span>{error}</span>
              </div>
              <button type="button" className="auth-error-close" onClick={() => setError(null)}>
                <X size={14} />
              </button>
            </div>
          )}

          <form onSubmit={handleSubmit((d) => mutation.mutate(d))} className="auth-form">
            <div className="form-group">
              <label className="form-label">Email Address</label>
              <div className="input-with-icon">
                <input
                  id="forgot-email"
                  className={`form-input${error ? ' input-error' : ''}`}
                  type="email"
                  placeholder="you@example.com"
                  style={{ paddingLeft: 44 }}
                  {...register('email', { required: 'Email is required' })}
                />
                <Mail size={16} style={{ position: 'absolute', left: 14, top: '50%', transform: 'translateY(-50%)', color: 'var(--text-muted)' }} />
              </div>
              {errors.email && <span className="form-error">{errors.email.message}</span>}
            </div>

            <button
              id="forgot-submit"
              type="submit"
              className="btn btn-primary w-full btn-lg"
              style={{ marginTop: 8 }}
              disabled={mutation.isPending}
            >
              {mutation.isPending ? <span className="spinner" /> : 'Send Reset Link'}
            </button>
          </form>

          <p className="auth-footer" style={{ marginTop: 24 }}>
            <Link to="/login" style={{ display: 'inline-flex', alignItems: 'center', gap: 6, color: 'var(--text-secondary)' }}>
              <ArrowLeft size={14} /> Back to Login
            </Link>
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

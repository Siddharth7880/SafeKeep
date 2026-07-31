import { useForm } from 'react-hook-form';
import { Link, useNavigate } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import { authApi } from '../api/client';
import toast from 'react-hot-toast';
import { Shield, Eye, EyeOff, CheckCircle, XCircle } from 'lucide-react';
import { useState } from 'react';
import './AuthPage.css';

const passwordChecks = [
  { label: 'At least 8 characters', test: (v) => v.length >= 8 },
  { label: 'Uppercase letter',       test: (v) => /[A-Z]/.test(v) },
  { label: 'Lowercase letter',       test: (v) => /[a-z]/.test(v) },
  { label: 'Number',                 test: (v) => /\d/.test(v) },
  { label: 'Special character',      test: (v) => /[@$!%*?&]/.test(v) },
];

export default function RegisterPage() {
  const navigate = useNavigate();
  const [showPassword, setShowPassword] = useState(false);
  const [password, setPassword] = useState('');

  const { register, handleSubmit, getValues, formState: { errors } } = useForm();

  const registerMutation = useMutation({
    mutationFn: authApi.register,
    onSuccess: () => {
      navigate('/verify-email', { state: { email: getValues('email') } });
    },
    onError: (err) => {
      toast.error(err.response?.data?.message || 'Registration failed');
    },
  });

  const onSubmit = (data) => registerMutation.mutate(data);

  const passedChecks = passwordChecks.filter(c => c.test(password)).length;
  const strengthColor = passedChecks === 5 ? 'var(--success)'
                      : passedChecks >= 3 ? 'var(--warning)'
                      : 'var(--danger)';

  return (
    <div className="auth-page">
      {/* Left side: Form */}
      <div className="auth-left">
        <div className="auth-card" style={{ maxWidth: 460 }}>
          <div className="auth-logo">
            <Shield size={32} className="logo-icon" />
            <span>SafeKeep</span>
          </div>
          <h2 style={{ marginBottom: 4 }}>Create your account</h2>
          <p className="text-muted text-sm" style={{ marginBottom: 28 }}>Start protecting your digital legacy</p>

          <form onSubmit={handleSubmit(onSubmit)} className="auth-form">
            <div className="form-group">
              <label className="form-label">Full Name</label>
              <input
                id="fullName"
                className="form-input"
                placeholder="John Doe"
                {...register('fullName', { required: 'Full name is required', minLength: { value: 2, message: 'Min 2 characters' } })}
              />
              {errors.fullName && <span className="form-error">{errors.fullName.message}</span>}
            </div>

            <div className="form-group">
              <label className="form-label">Email Address</label>
              <input
                id="email"
                className="form-input"
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
                  className="form-input"
                  type={showPassword ? 'text' : 'password'}
                  placeholder="Strong password"
                  {...register('password', {
                    required: 'Password is required',
                    minLength: { value: 8, message: 'Min 8 characters' },
                    onChange: (e) => setPassword(e.target.value),
                  })}
                />
                <button type="button" className="input-icon-btn" onClick={() => setShowPassword(!showPassword)}>
                  {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                </button>
              </div>
              {errors.password && <span className="form-error">{errors.password.message}</span>}

              {/* Password strength bar */}
              {password.length > 0 && (
                <div style={{ marginTop: 8 }}>
                  <div style={{ height: 3, background: 'rgba(255,255,255,0.08)', borderRadius: 100, overflow: 'hidden' }}>
                    <div style={{
                      height: '100%',
                      width: `${(passedChecks / 5) * 100}%`,
                      background: strengthColor,
                      borderRadius: 100,
                      transition: 'width 0.4s cubic-bezier(0.34,1.56,0.64,1)',
                    }} />
                  </div>
                  <div className="password-checks" style={{ marginTop: 10 }}>
                    {passwordChecks.map(({ label, test }) => (
                      <div key={label} className={`password-check ${test(password) ? 'check-pass' : 'check-fail'}`}>
                        {test(password) ? <CheckCircle size={12} /> : <XCircle size={12} />}
                        <span>{label}</span>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>

            <button
              id="register-submit"
              type="submit"
              className="btn btn-primary w-full btn-lg"
              style={{ marginTop: 8 }}
              disabled={registerMutation.isPending}
            >
              {registerMutation.isPending ? <span className="spinner" /> : 'Create Account'}
            </button>
          </form>

          <div className="auth-security-badges">
            <div className="auth-security-badge">
              <Shield size={14} /> End-to-end encrypted storage
            </div>
          </div>

          <p className="auth-footer">
            Already have an account? <Link to="/login">Sign in</Link>
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
            <h3 className="auth-quote">"Ensure your most important data always reaches the right hands."</h3>
            <span className="auth-quote-author">Zero Knowledge Trust</span>
          </div>
        </div>
      </div>
    </div>
  );
}

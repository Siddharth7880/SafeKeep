import { useState, useRef, useEffect } from 'react';
import { useLocation, useNavigate, Navigate } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import { authApi } from '../api/client';
import { useAuthStore } from '../store/authStore';
import { Shield, Mail, CheckCircle } from 'lucide-react';
import toast from 'react-hot-toast';
import './AuthPage.css';

export default function VerifyEmailPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const setAuth = useAuthStore((s) => s.setAuth);

  const email = location.state?.email || new URLSearchParams(location.search).get('email');
  const queryCode = new URLSearchParams(location.search).get('code');
  
  const [digits, setDigits] = useState(['', '', '', '', '', '']);
  const inputsRef = useRef([]);

  const code = digits.join('');
  const [autoVerifyFired, setAutoVerifyFired] = useState(false);

  const verifyMutation = useMutation({
    mutationFn: authApi.verifyEmail,
    onSuccess: (res) => {
      const { data } = res.data;
      setAuth(data.user, data.accessToken, data.refreshToken);
      toast.success('Email verified! Welcome to SafeKeep! 🎉');
      navigate('/dashboard');
    },
    onError: (err) => {
      toast.error(err.response?.data?.message || 'Invalid code. Please try again.');
      setDigits(['', '', '', '', '', '']);
      inputsRef.current[0]?.focus();
    }
  });

  useEffect(() => {
    if (email && queryCode && queryCode.length === 6 && !autoVerifyFired) {
      setAutoVerifyFired(true);
      setDigits(queryCode.split(''));
      verifyMutation.mutate({ email, code: queryCode });
    }
  }, [email, queryCode, autoVerifyFired, verifyMutation]);

  const handleDigitChange = (index, value) => {
    if (!/^\d?$/.test(value)) return;
    const next = [...digits];
    next[index] = value;
    setDigits(next);
    if (value && index < 5) inputsRef.current[index + 1]?.focus();
    if (next.every(d => d) && next.join('').length === 6) {
      verifyMutation.mutate({ email, code: next.join('') });
    }
  };

  const handleKeyDown = (index, e) => {
    if (e.key === 'Backspace' && !digits[index] && index > 0) {
      inputsRef.current[index - 1]?.focus();
    }
  };

  const handlePaste = (e) => {
    const pasted = e.clipboardData.getData('text').replace(/\D/g, '').slice(0, 6);
    if (pasted.length === 6) {
      setDigits(pasted.split(''));
      verifyMutation.mutate({ email, code: pasted });
    }
  };

  if (!email) return <Navigate to="/register" replace />;

  return (
    <div className="auth-page" style={{ justifyContent: 'center', alignItems: 'center' }}>
      <div className="auth-bg-glow" />
      <div className="verify-card">
        <div className="verify-icon">
          <Mail size={32} />
        </div>

        <div style={{ marginBottom: 8 }}>
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: 12, fontWeight: 700, textTransform: 'uppercase', letterSpacing: 1.5, color: 'var(--primary)', marginBottom: 12 }}>
            <Shield size={12} /> Email Verification
          </span>
          <h2 style={{ fontSize: '1.75rem', marginBottom: 10 }}>Check your inbox</h2>
          <p className="text-muted text-sm" style={{ lineHeight: 1.7 }}>
            We've sent a 6-digit verification code to{' '}
            <strong style={{ color: 'var(--text-primary)' }}>{email}</strong>
          </p>
        </div>

        <div className="code-input-row" onPaste={handlePaste}>
          {digits.map((d, i) => (
            <input
              key={i}
              ref={el => inputsRef.current[i] = el}
              className="code-digit"
              type="text"
              inputMode="numeric"
              maxLength={1}
              value={d}
              onChange={(e) => handleDigitChange(i, e.target.value)}
              onKeyDown={(e) => handleKeyDown(i, e)}
              autoFocus={i === 0}
            />
          ))}
        </div>

        <button
          type="button"
          className="btn btn-primary btn-lg w-full"
          disabled={verifyMutation.isPending || code.length !== 6}
          onClick={() => verifyMutation.mutate({ email, code })}
          style={{ marginTop: 8 }}
        >
          {verifyMutation.isPending ? (
            <span className="spinner" />
          ) : (
            <>
              <CheckCircle size={16} />
              Verify & Enter Vault
            </>
          )}
        </button>

        <p className="text-muted text-sm" style={{ marginTop: 20 }}>
          Didn't get the code? Check your spam folder or{' '}
          <button
            style={{ background: 'none', border: 'none', color: 'var(--primary-light)', fontWeight: 600, cursor: 'pointer', fontSize: 'inherit', padding: 0 }}
            onClick={() => toast.error('Re-registration required to resend.')}
          >
            go back
          </button>
          .
        </p>
      </div>
    </div>
  );
}

import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { checkinApi, authApi } from '../api/client';
import { useForm } from 'react-hook-form';
import { useAuthStore } from '../store/authStore';
import { Save, Bell, Clock, Trash2, Settings2 } from 'lucide-react';
import toast from 'react-hot-toast';
import { useNavigate } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';

export default function SettingsPage() {
  const queryClient = useQueryClient();
  const updateUser = useAuthStore((s) => s.updateUser);
  const logout = useAuthStore((s) => s.logout);
  const navigate = useNavigate();

  const [isDeleting, setIsDeleting] = useState(false);
  const [deletePassword, setDeletePassword] = useState('');

  const { data: profileRes, isLoading } = useQuery({ queryKey: ['profile'], queryFn: authApi.getProfile });
  const profile = profileRes?.data?.data;

  const { register, handleSubmit } = useForm({
    values: {
      checkinIntervalDays: profile?.checkinIntervalDays || 7,
      gracePeriodDays: profile?.gracePeriodDays || 3,
      emailNotificationsEnabled: profile?.emailNotificationsEnabled ?? true,
    }
  });

  const updateMutation = useMutation({
    mutationFn: checkinApi.updateSettings,
    onSuccess: (res) => {
      const user = res.data?.data;
      if (user) updateUser(user);
      queryClient.invalidateQueries({ queryKey: ['profile'] });
      toast.success('Settings saved successfully');
    },
    onError: () => toast.error('Failed to save settings'),
  });

  const deleteAccountMutation = useMutation({
    mutationFn: authApi.deleteAccount,
    onSuccess: () => {
      logout();
      navigate('/login');
      toast.success('Account successfully deleted');
    },
    onError: (err) => {
      const msg = err.response?.data?.message || 'Failed to delete account';
      toast.error(msg);
    },
  });

  const onSubmit = (data) => updateMutation.mutate(data);

  const handleConfirmDelete = () => {
    if (!deletePassword) {
      toast.error('Please enter your password to confirm deletion');
      return;
    }
    deleteAccountMutation.mutate(deletePassword);
  };

  if (isLoading) return (
    <div className="page-container" style={{ maxWidth: 640 }}>
      <div className="skeleton-shimmer h-16 w-full mb-6"></div>
      <div className="skeleton-shimmer h-48 w-full mb-6"></div>
      <div className="skeleton-shimmer h-64 w-full"></div>
    </div>
  );

  return (
    <div className="page-container" style={{ maxWidth: 640 }}>
      <div className="page-header">
        <div>
          <h2 className="flex items-center gap-3"><Settings2 className="text-primary" /> Settings</h2>
          <p className="text-muted text-sm mt-1">Configure your check-in schedule and notifications.</p>
        </div>
      </div>

      <form onSubmit={handleSubmit(onSubmit)} className="flex-col gap-6 stagger-children">

        {/* Check-In Schedule */}
        <div className="card glass-panel">
          <div className="flex items-center gap-2 mb-4">
            <Clock size={18} className="text-primary" />
            <h3>Check-In Schedule</h3>
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
            <div className="form-group">
              <label className="form-label">Check-In Interval (days)</label>
              <input className="form-input" type="number" min="1" max="365" {...register('checkinIntervalDays')} />
              <span className="text-xs text-muted mt-1">How often you need to check in</span>
            </div>
            <div className="form-group">
              <label className="form-label">Grace Period (days)</label>
              <input className="form-input" type="number" min="1" max="30" {...register('gracePeriodDays')} />
              <span className="text-xs text-muted mt-1">Extra time before release triggers</span>
            </div>
          </div>
        </div>

        {/* Notifications */}
        <div className="card glass-panel">
          <div className="flex items-center gap-2 mb-4">
            <Bell size={18} className="text-primary" />
            <h3>Notifications</h3>
          </div>
          <div className="flex-col gap-4">
            <label className="flex items-center gap-3" style={{ cursor: 'pointer' }}>
              <input type="checkbox" style={{ accentColor: 'var(--primary)', width: 16, height: 16 }} {...register('emailNotificationsEnabled')} />
              <div>
                <div style={{ fontWeight: 500, fontSize: 14 }}>Email Notifications</div>
                <div className="text-muted text-sm">Receive check-in reminders by email</div>
              </div>
            </label>
          </div>
        </div>

        <motion.button 
          whileHover={{ scale: 1.02 }}
          whileTap={{ scale: 0.95 }}
          type="submit" className="btn btn-primary btn-lg" style={{ alignSelf: 'flex-end' }}
          disabled={updateMutation.isPending}
        >
          {updateMutation.isPending ? <span className="spinner" /> : <><Save size={16} /> Save Settings</>}
        </motion.button>
      </form>

      {/* Developer / Test Zone */}
      <div className="card glass-panel" style={{ marginTop: 40, borderColor: 'rgba(234, 179, 8, 0.2)' }}>
        <div className="flex items-center gap-2 mb-4">
          <Settings2 size={18} className="text-warning" style={{ color: '#eab308' }} />
          <h3 style={{ color: '#eab308' }}>Developer / Test Actions</h3>
        </div>
        <p className="text-muted text-sm mb-4">
          Instantly simulate a missed deadline and trigger the final release. 
          This will email recipients if they have items assigned to them.
        </p>
        <button 
          type="button" 
          className="btn btn-ghost" 
          style={{ border: '1px solid #eab308', color: '#eab308' }}
          onClick={() => {
            import('../api/client').then(({ testApi }) => {
              const loadingToast = toast.loading('Triggering release...');
              testApi.triggerRelease()
                .then(() => {
                  toast.success('Test release triggered successfully! Check recipient emails.', { id: loadingToast });
                  queryClient.invalidateQueries({ queryKey: ['profile'] });
                })
                .catch((e) => toast.error(e.response?.data?.message || 'Failed to trigger release', { id: loadingToast }));
            });
          }}
        >
          Force Trigger Release (Test)
        </button>
      </div>

      {/* Danger Zone */}
      <div className="card glass-panel" style={{ marginTop: 40, borderColor: 'rgba(239, 68, 68, 0.2)' }}>
        <div className="flex items-center gap-2 mb-4">
          <Trash2 size={18} className="text-danger" />
          <h3 className="text-danger">Danger Zone</h3>
        </div>
        <p className="text-muted text-sm mb-4">
          Deleting your account is permanent. All your data, vault items, and recipients will be erased.
        </p>

        <AnimatePresence mode="wait">
          {!isDeleting ? (
            <motion.div key="delete-btn" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
              <button type="button" className="btn btn-danger" onClick={() => setIsDeleting(true)}>
                Delete Account
              </button>
            </motion.div>
          ) : (
            <motion.div 
              key="delete-confirm"
              initial={{ opacity: 0, height: 0 }}
              animate={{ opacity: 1, height: 'auto' }}
              exit={{ opacity: 0, height: 0 }}
              style={{ overflow: 'hidden' }}
            >
              <div style={{ background: 'rgba(239, 68, 68, 0.05)', padding: 16, borderRadius: 8, marginTop: 16, border: '1px solid rgba(239, 68, 68, 0.1)' }}>
                <h4 className="text-danger mb-2 text-sm" style={{ fontWeight: 600 }}>Confirm Account Deletion</h4>
                <p className="text-sm text-danger mb-4" style={{ opacity: 0.8 }}>
                  Please enter your password to confirm this irreversible action.
                </p>
                <div className="form-group mb-4">
                  <input 
                    type="password" 
                    className="form-input" 
                    placeholder="Your Password"
                    value={deletePassword}
                    onChange={(e) => setDeletePassword(e.target.value)}
                  />
                </div>
                <div className="flex gap-2">
                  <button type="button" className="btn btn-danger" onClick={handleConfirmDelete} disabled={deleteAccountMutation.isPending}>
                    {deleteAccountMutation.isPending ? <span className="spinner" /> : 'Confirm Deletion'}
                  </button>
                  <button type="button" className="btn btn-ghost" onClick={() => { setIsDeleting(false); setDeletePassword(''); }} disabled={deleteAccountMutation.isPending}>
                    Cancel
                  </button>
                </div>
              </div>
            </motion.div>
          )}
        </AnimatePresence>
      </div>
    </div>
  );
}

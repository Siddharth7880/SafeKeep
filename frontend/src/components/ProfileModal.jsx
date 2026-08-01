import { useState, useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { authApi, API_BASE } from '../api/client';
import { useAuthStore } from '../store/authStore';
import { X, Save, User, Camera } from 'lucide-react';
import toast from 'react-hot-toast';
import { motion, AnimatePresence } from 'framer-motion';

export default function ProfileModal({ isOpen, onClose }) {
  const queryClient = useQueryClient();
  const { user, updateUser } = useAuthStore();
  const [isEditing, setIsEditing] = useState(false);

  const { register, handleSubmit, reset } = useForm({
    defaultValues: {
      fullName: user?.fullName || ''
    }
  });

  useEffect(() => {
    if (user) {
      reset({ fullName: user.fullName || '' });
    }
  }, [user, reset]);

  const updateMutation = useMutation({
    mutationFn: authApi.updateProfile,
    onSuccess: (res) => {
      updateUser(res.data.data);
      queryClient.invalidateQueries({ queryKey: ['profile'] });
      toast.success('Profile updated');
      setIsEditing(false);
    },
    onError: () => toast.error('Failed to update profile')
  });

  const onSubmit = (data) => {
    updateMutation.mutate(data);
  };

  const handlePhotoUpload = async (e) => {
    if (e.target.files && e.target.files[0]) {
      const formData = new FormData();
      formData.append('file', e.target.files[0]);
      const toastId = toast.loading('Uploading photo...');
      try {
        const res = await authApi.uploadProfilePhoto(formData);
        updateUser(res.data.data);
        queryClient.invalidateQueries({ queryKey: ['profile'] });
        toast.success('Photo uploaded', { id: toastId });
      } catch (err) {
        toast.error('Failed to upload photo', { id: toastId });
      }
    }
  };

  if (!isOpen || !user) return null;

  return (
    <div className="modal-overlay profile-modal-overlay" onClick={(e) => e.target === e.currentTarget && onClose()}>
      <motion.div 
        initial={{ opacity: 0, scale: 0.95, y: 10 }}
        animate={{ opacity: 1, scale: 1, y: 0 }}
        exit={{ opacity: 0, scale: 0.95, y: 10 }}
        className="modal-card profile-modal-card glass-panel"
        style={{ maxWidth: 400 }}
      >
        <div className="modal-header">
          <h3 className="flex items-center gap-2"><User size={18} /> User Profile</h3>
          <button className="modal-close" onClick={onClose}><X size={18} /></button>
        </div>

        <div className="profile-modal-content" style={{ padding: '20px 0' }}>
          <div className="profile-photo-section" style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', marginBottom: 24 }}>
            <div className="profile-photo-wrapper" style={{ position: 'relative', width: 96, height: 96, marginBottom: 16 }}>
              {user.profilePhotoUrl ? (
                <img src={`${API_BASE}${user.profilePhotoUrl}`} alt="Profile" style={{ width: '100%', height: '100%', borderRadius: '50%', objectFit: 'cover', border: '3px solid var(--primary)' }} />
              ) : (
                <div style={{ width: '100%', height: '100%', borderRadius: '50%', background: 'linear-gradient(135deg, var(--primary), var(--primary-dark))', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 32, fontWeight: 'bold', color: 'white', border: '3px solid var(--primary-glow)' }}>
                  {user.fullName?.charAt(0).toUpperCase() || 'U'}
                </div>
              )}
              <label style={{ position: 'absolute', bottom: 0, right: 0, background: 'var(--primary)', color: 'white', width: 32, height: 32, borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer', border: '2px solid var(--bg-surface)', transition: 'transform 0.2s ease' }} className="hover-scale">
                <Camera size={14} />
                <input type="file" accept="image/*" onChange={handlePhotoUpload} hidden />
              </label>
            </div>
            <div className="profile-basic-info" style={{ textAlign: 'center' }}>
              <h2 style={{ fontSize: 20, marginBottom: 4 }}>{user.fullName}</h2>
              <p className="text-muted text-sm">{user.email}</p>
            </div>
          </div>

          <div className="profile-actions">
            {!isEditing ? (
              <button className="btn btn-primary w-full btn-ripple" onClick={() => setIsEditing(true)}>
                Edit Profile Info
              </button>
            ) : (
              <form onSubmit={handleSubmit(onSubmit)} className="profile-edit-form p-4" style={{ background: 'var(--bg-card-hover)', borderRadius: 12, border: '1px solid var(--border)' }}>
                <div className="form-group mb-4">
                  <label className="form-label">Full Name</label>
                  <input className="form-input" type="text" {...register('fullName', { required: true })} />
                </div>
                <div className="flex gap-2">
                  <button type="button" className="btn btn-ghost w-full" onClick={() => { setIsEditing(false); reset(); }}>
                    Cancel
                  </button>
                  <button type="submit" className="btn btn-primary w-full" disabled={updateMutation.isPending}>
                    {updateMutation.isPending ? <span className="spinner" /> : <><Save size={16} /> Save</>}
                  </button>
                </div>
              </form>
            )}
          </div>
        </div>
      </motion.div>
    </div>
  );
}

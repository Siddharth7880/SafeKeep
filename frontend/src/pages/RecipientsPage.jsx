import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { recipientApi, vaultApi } from '../api/client';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Plus, UserPlus, Trash2, Edit, Mail, Phone, User, FileKey, X, Save } from 'lucide-react';
import toast from 'react-hot-toast';
import { motion, AnimatePresence } from 'framer-motion';

export default function RecipientsPage() {
  const queryClient = useQueryClient();
  const [showForm, setShowForm] = useState(false);
  const [editRecipient, setEditRecipient] = useState(null);
  const { register, handleSubmit, reset, setValue } = useForm();

  const { data: res, isLoading } = useQuery({
    queryKey: ['recipients'],
    queryFn: recipientApi.list,
  });
  const recipients = res?.data?.data || [];

  const { data: vaultRes } = useQuery({
    queryKey: ['vault-items'],
    queryFn: vaultApi.list,
  });
  const vaultItems = vaultRes?.data?.data || [];

  const [assignRecipient, setAssignRecipient] = useState(null);
  const [selectedItems, setSelectedItems] = useState([]);

  const createMutation = useMutation({
    mutationFn: recipientApi.create,
    onSuccess: () => { queryClient.invalidateQueries(['recipients']); toast.success('Recipient added'); setShowForm(false); reset(); },
    onError: (e) => toast.error(e.response?.data?.message || 'Failed to add recipient'),
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, data }) => recipientApi.update(id, data),
    onSuccess: () => { queryClient.invalidateQueries(['recipients']); toast.success('Recipient updated'); setEditRecipient(null); reset(); },
  });

  const deleteMutation = useMutation({
    mutationFn: recipientApi.delete,
    onSuccess: () => { queryClient.invalidateQueries(['recipients']); toast.success('Recipient removed'); },
  });

  const onSubmit = (data) => {
    if (editRecipient) { updateMutation.mutate({ id: editRecipient.id, data }); }
    else { createMutation.mutate(data); }
  };

  const startEdit = (r) => {
    setEditRecipient(r);
    setValue('name', r.name); setValue('email', r.email);
    setValue('phone', r.phone); setValue('relationship', r.relationship);
    setShowForm(true);
  };

  const startAssign = (r) => {
    const assignedIds = vaultItems
      .filter(item => item.recipients?.some(rec => rec.id === r.id))
      .map(item => item.id);
    setSelectedItems(assignedIds);
    setAssignRecipient(r);
  };

  const assignMutation = useMutation({
    mutationFn: ({ id, itemIds }) => recipientApi.assignItems(id, itemIds),
    onSuccess: () => {
      queryClient.invalidateQueries(['recipients']);
      queryClient.invalidateQueries(['vault-items']);
      toast.success('Items assigned successfully');
      setAssignRecipient(null);
    },
    onError: (e) => toast.error(e.response?.data?.message || 'Failed to assign items'),
  });

  return (
    <div className="page-container">
      <div className="page-header">
        <div>
          <h2>👥 Recipients</h2>
          <p className="text-muted text-sm" style={{ marginTop: 4 }}>Trusted contacts who receive your vault contents when released.</p>
        </div>
        <button id="add-recipient" className="btn btn-primary" onClick={() => { setShowForm(!showForm); setEditRecipient(null); reset(); }}>
          <Plus size={16} /> Add Recipient
        </button>
      </div>

      <AnimatePresence>
        {showForm && (
          <motion.div 
            initial={{ opacity: 0, height: 0 }}
            animate={{ opacity: 1, height: 'auto' }}
            exit={{ opacity: 0, height: 0 }}
            className="overflow-hidden"
            style={{ marginBottom: 24 }}
          >
            <div className="card glass-panel">
              <h3 style={{ marginBottom: 20 }}>{editRecipient ? 'Edit Recipient' : 'New Recipient'}</h3>
              <form onSubmit={handleSubmit(onSubmit)} style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
                <div className="form-group">
                  <label className="form-label">Full Name *</label>
                  <input className="form-input" placeholder="Jane Doe" {...register('name', { required: true })} />
                </div>
                <div className="form-group">
                  <label className="form-label">Email Address *</label>
                  <input className="form-input" type="email" placeholder="jane@example.com" {...register('email', { required: true })} />
                </div>
                <div className="form-group">
                  <label className="form-label">Phone (optional)</label>
                  <input className="form-input" placeholder="+1 555 000 0000" {...register('phone')} />
                </div>
                <div className="form-group">
                  <label className="form-label">Relationship</label>
                  <input className="form-input" placeholder="e.g. Spouse, Lawyer, Friend" {...register('relationship')} />
                </div>
                <div style={{ gridColumn: 'span 2', display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
                  <button type="button" className="btn btn-ghost" onClick={() => { setShowForm(false); setEditRecipient(null); reset(); }}>Cancel</button>
                  <motion.button whileHover={{ scale: 1.02 }} whileTap={{ scale: 0.95 }} type="submit" className="btn btn-primary" disabled={createMutation.isPending || updateMutation.isPending}>
                    <UserPlus size={14} /> {editRecipient ? 'Update' : 'Add Recipient'}
                  </motion.button>
                </div>
              </form>
            </div>
          </motion.div>
        )}
      </AnimatePresence>

      {isLoading ? (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12, paddingTop: 20 }}>
          <div className="skeleton-shimmer h-24 w-full"></div>
          <div className="skeleton-shimmer h-24 w-full"></div>
          <div className="skeleton-shimmer h-24 w-full"></div>
        </div>
      ) : recipients.length === 0 ? (
        <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="empty-state">
          <User size={48} className="empty-icon" />
          <h3>No recipients yet</h3>
          <p className="text-muted text-sm">Add trusted contacts who should receive your vault contents.</p>
        </motion.div>
      ) : (
        <motion.div 
          style={{ display: 'flex', flexDirection: 'column', gap: 12 }}
          initial="hidden" animate="show"
          variants={{
            hidden: { opacity: 0 },
            show: { opacity: 1, transition: { staggerChildren: 0.1 } }
          }}
        >
          {recipients.map(r => (
            <motion.div 
              key={r.id} 
              className="card glass-panel" 
              style={{ display: 'flex', alignItems: 'center', gap: 16, padding: '16px 20px' }}
              variants={{
                hidden: { opacity: 0, x: -20 },
                show: { opacity: 1, x: 0, transition: { type: 'spring', stiffness: 100 } }
              }}
            >
              <div style={{ width: 44, height: 44, borderRadius: '50%', background: 'var(--primary-glow)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'var(--primary-light)', fontWeight: 700, fontSize: 18, flexShrink: 0, border: '1px solid rgba(124,111,205,0.2)' }}>
                {r.name.charAt(0).toUpperCase()}
              </div>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontWeight: 600, fontSize: 15 }}>{r.name}</div>
                <div style={{ display: 'flex', gap: 16, marginTop: 4, flexWrap: 'wrap' }}>
                  <span className="text-muted text-sm flex items-center gap-2"><Mail size={12} />{r.email}</span>
                  {r.phone && <span className="text-muted text-sm flex items-center gap-2"><Phone size={12} />{r.phone}</span>}
                  {r.relationship && <span className="text-sm" style={{ color: 'var(--primary-light)' }}>{r.relationship}</span>}
                </div>
              </div>
              <div style={{ display: 'flex', gap: 8, flexShrink: 0 }}>
                <span className={`badge ${r.notifyOnRelease ? 'badge-active' : 'badge-paused'}`} style={{ fontSize: 11 }}>
                  {r.notifyOnRelease ? '● Notified' : '○ Silent'}
                </span>
                {r.assignedVaultItemCount !== undefined && (
                  <span className="badge badge-paused" style={{ fontSize: 11 }}>{r.assignedVaultItemCount} items</span>
                )}
                <button className="btn btn-ghost btn-sm" onClick={() => startAssign(r)}><FileKey size={14} /> Assign</button>
                <button className="btn btn-ghost btn-sm" onClick={() => startEdit(r)}><Edit size={14} /></button>
                <button className="btn btn-danger btn-sm" onClick={() => deleteMutation.mutate(r.id)}><Trash2 size={14} /></button>
              </div>
            </motion.div>
          ))}
        </motion.div>
      )}

      {/* Assign Items Modal */}
      {assignRecipient && (
        <div className="modal-overlay" onClick={(e) => e.target === e.currentTarget && setAssignRecipient(null)}>
          <div className="modal-card animate-scale-in">
            <div className="modal-header">
              <div>
                <h3 style={{ marginBottom: 4 }}>🔑 Assign Vault Items</h3>
                <p className="text-muted text-sm">Select items to share with {assignRecipient.name}</p>
              </div>
              <button className="modal-close" onClick={() => setAssignRecipient(null)}><X size={18} /></button>
            </div>
            
            <div style={{ display: 'flex', flexDirection: 'column', gap: '12px', maxHeight: '400px', overflowY: 'auto', marginBottom: '24px' }}>
              {vaultItems.length === 0 ? (
                <p className="text-muted text-sm">No vault items available. Add some in your Vault first.</p>
              ) : (
                vaultItems.map(item => (
                  <label key={item.id} style={{ display: 'flex', alignItems: 'center', gap: '12px', padding: '12px', background: 'var(--bg-surface)', border: '1px solid var(--border)', borderRadius: 'var(--radius-md)', cursor: 'pointer', transition: 'var(--transition)' }} className="card-static">
                    <input 
                      type="checkbox" 
                      style={{ accentColor: 'var(--primary)', width: '16px', height: '16px' }}
                      checked={selectedItems.includes(item.id)}
                      onChange={(e) => {
                        if (e.target.checked) {
                          setSelectedItems(prev => [...prev, item.id]);
                        } else {
                          setSelectedItems(prev => prev.filter(id => id !== item.id));
                        }
                      }}
                    />
                    <div>
                      <div style={{ fontWeight: 600, fontSize: '14px' }}>{item.label}</div>
                      <div className="text-muted text-xs" style={{ marginTop: '2px' }}>{item.contentType}</div>
                    </div>
                  </label>
                ))
              )}
            </div>
            
            <button 
              className="btn btn-primary btn-ripple w-full" 
              onClick={() => assignMutation.mutate({ id: assignRecipient.id, itemIds: selectedItems })}
              disabled={assignMutation.isPending}
            >
              {assignMutation.isPending ? <span className="spinner" /> : <><Save size={14} /> Save Assignments</>}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

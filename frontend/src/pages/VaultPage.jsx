import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { vaultApi, recipientApi } from '../api/client';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import {
  Plus, Lock, Eye, EyeOff, Trash2, FileText, Key, MessageSquare, AlertCircle,
  X, Copy, Check, ShieldAlert, Download, Paperclip, Edit2
} from 'lucide-react';
import toast from 'react-hot-toast';
import { motion, AnimatePresence } from 'framer-motion';
import './VaultPage.css';

const contentTypeIcons = {
  TEXT_MESSAGE: MessageSquare,
  CREDENTIALS: Key,
  DOCUMENT_NOTE: FileText,
  FINAL_INSTRUCTIONS: AlertCircle,
  PERSONAL_MESSAGE: MessageSquare,
};

const contentTypeLabels = {
  TEXT_MESSAGE: 'Text Message',
  CREDENTIALS: 'Credentials / Password',
  DOCUMENT_NOTE: 'Document Note',
  FINAL_INSTRUCTIONS: 'Final Instructions',
  PERSONAL_MESSAGE: 'Personal Message',
};

export default function VaultPage() {
  const queryClient = useQueryClient();
  const [showForm, setShowForm] = useState(false);
  const [editItem, setEditItem] = useState(null);
  const [viewItem, setViewItem] = useState(null);
  const [vaultPassword, setVaultPassword] = useState('');
  const [showVaultPw, setShowVaultPw] = useState(false);

  // View modal state
  const [viewPassword, setViewPassword] = useState('');
  const [showViewPw, setShowViewPw] = useState(false);
  const [decryptedContent, setDecryptedContent] = useState(null);
  const [copied, setCopied] = useState(false);
  const [decryptedFileUrl, setDecryptedFileUrl] = useState(null);
  const [fileType, setFileType] = useState(null);
  const [decryptedFileText, setDecryptedFileText] = useState(null);

  const { data: itemsRes, isLoading } = useQuery({
    queryKey: ['vault-items'],
    queryFn: vaultApi.list,
  });
  const { data: recipientsRes } = useQuery({
    queryKey: ['recipients'],
    queryFn: recipientApi.list,
  });

  const items = itemsRes?.data?.data || [];
  const recipients = recipientsRes?.data?.data || [];

  const { register, handleSubmit, reset, formState: { errors } } = useForm();

  const createMutation = useMutation({
    mutationFn: ({ formData, password }) => vaultApi.create(formData, password),
    onSuccess: () => {
      queryClient.invalidateQueries(['vault-items']);
      toast.success('Item stored securely in your vault');
      setShowForm(false); setVaultPassword(''); reset();
    },
    onError: (e) => toast.error(e.response?.data?.message || 'Failed to store item'),
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, payload, password }) => vaultApi.update(id, payload, password),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['vault-items'] });
      setEditItem(null);
      setVaultPassword('');
      toast.success('Vault item updated');
      reset();
    },
    onError: (err) => toast.error(err.response?.data?.message || 'Failed to update item')
  });

  const [itemToDelete, setItemToDelete] = useState(null);
  const [deleteVaultPassword, setDeleteVaultPassword] = useState('');

  const deleteMutation = useMutation({
    mutationFn: ({id, password}) => vaultApi.delete(id, password),
    onSuccess: () => { 
      queryClient.invalidateQueries({ queryKey: ['vault-items'] }); 
      toast.success('Item deleted securely');
      setItemToDelete(null);
      setDeleteVaultPassword('');
    },
    onError: (e) => toast.error('Failed to delete: ' + (e.response?.data?.message || 'wrong password')),
  });

  const [isDecrypting, setIsDecrypting] = useState(false);

  const handleCloseViewModal = () => {
    setViewItem(null);
    setViewPassword('');
    setShowViewPw(false);
    setDecryptedContent(null);
    setDecryptedFileUrl(null);
    setFileType(null);
    setDecryptedFileText(null);
    setCopied(false);
  };

  const handleDecrypt = async () => {
    if (!viewPassword) { toast.error('Please enter your vault password'); return; }
    setIsDecrypting(true);
    try {
      if (viewItem.hasContent) {
        const res = await vaultApi.get(viewItem.id, viewPassword);
        setDecryptedContent(res.data.data.content);
      }
      
      if (viewItem.hasFile) {
        const fileRes = await vaultApi.download(viewItem.id, viewPassword);
        const name = (viewItem.originalFileName || '').toLowerCase();
        let mimeType = 'application/octet-stream';
        if (name.endsWith('.pdf')) mimeType = 'application/pdf';
        else if (name.endsWith('.png')) mimeType = 'image/png';
        else if (name.endsWith('.jpg') || name.endsWith('.jpeg')) mimeType = 'image/jpeg';
        else if (name.endsWith('.gif')) mimeType = 'image/gif';
        else if (name.endsWith('.webp')) mimeType = 'image/webp';
        else if (name.endsWith('.txt') || name.endsWith('.csv') || name.endsWith('.md')) mimeType = 'text/plain';

        const url = window.URL.createObjectURL(new Blob([fileRes.data], { type: mimeType }));
        setDecryptedFileUrl(url);
        if (name.endsWith('.png') || name.endsWith('.jpg') || name.endsWith('.jpeg') || name.endsWith('.gif') || name.endsWith('.webp')) {
          setFileType('image');
        } else if (name.endsWith('.pdf')) {
          setFileType('pdf');
        } else if (name.endsWith('.txt') || name.endsWith('.csv') || name.endsWith('.md')) {
          setFileType('text');
          const text = await fileRes.data.text();
          setDecryptedFileText(text);
        } else {
          setFileType('other');
        }
      }
      
      toast.success('Decrypted securely');
    } catch (e) {
      toast.error('Failed to decrypt — wrong password?');
      setDecryptedContent(null);
      setDecryptedFileUrl(null);
    } finally {
      setIsDecrypting(false);
    }
  };

  const handleCopy = () => {
    if (!decryptedContent) return;
    navigator.clipboard.writeText(decryptedContent).then(() => {
      setCopied(true);
      toast.success('Copied to clipboard');
      setTimeout(() => setCopied(false), 2000);
    });
  };

  const onSubmit = (data) => {
    if (!vaultPassword) { toast.error('Vault password is required to encrypt content'); return; }
    
    let rIds = data.recipientIds;
    if (!rIds) rIds = [];
    else if (!Array.isArray(rIds)) rIds = [rIds];
    
    const requestDto = { ...data, recipientIds: rIds.filter(Boolean) };
    delete requestDto.file;

    if (editItem) {
      updateMutation.mutate({ id: editItem.id, payload: requestDto, password: vaultPassword });
      return;
    }
    
    const formData = new FormData();
    formData.append('request', new Blob([JSON.stringify(requestDto)], { type: 'application/json' }));
    
    if (data.file && data.file.length > 0) {
      formData.append('file', data.file[0]);
    }
    
    if (!requestDto.content && (!data.file || data.file.length === 0)) {
      toast.error('Either text content or a file attachment is required');
      return;
    }

    createMutation.mutate({ 
      formData, 
      password: vaultPassword 
    });
  };

  return (
    <div className="page-container">
      <div className="page-header">
        <div>
          <h2>🔐 Vault</h2>
          <p className="text-muted text-sm" style={{ marginTop: 4 }}>All content is AES-256 encrypted. Only you can decrypt it.</p>
        </div>
        <button id="add-vault-item" className="btn btn-primary btn-ripple" onClick={() => { 
          if (showForm || editItem) {
            setShowForm(false);
            setEditItem(null);
            reset();
          } else {
            setShowForm(true);
            reset();
          }
        }}>
          {showForm || editItem ? <X size={16} /> : <Plus size={16} />} {showForm || editItem ? 'Cancel' : 'Add Item'}
        </button>
      </div>

      {/* Add/Edit Form */}
      <AnimatePresence>
        {(showForm || editItem) && (
          <motion.div 
            initial={{ opacity: 0, height: 0 }}
            animate={{ opacity: 1, height: 'auto' }}
            exit={{ opacity: 0, height: 0 }}
            className="vault-form-wrapper overflow-hidden"
          >
            <div className="card glass-panel vault-form-card">
              <h3 style={{ marginBottom: 20 }}>🔒 {editItem ? 'Edit Vault Item' : 'New Vault Item'}</h3>
            <form onSubmit={handleSubmit(onSubmit)} className="vault-form">
              <div className="vault-form-row">
                <div className="form-group">
                  <label className="form-label">Label</label>
                  <input className="form-input" placeholder="e.g. Gmail Password" {...register('label', { required: true })} />
                </div>
                <div className="form-group">
                  <label className="form-label">Content Type</label>
                  <select className="form-input" {...register('contentType', { required: true })}>
                    {Object.entries(contentTypeLabels).map(([v, l]) => (
                      <option key={v} value={v}>{l}</option>
                    ))}
                  </select>
                </div>
              </div>

              <div className="form-group">
                <label className="form-label">Content (will be encrypted)</label>
                <textarea className="form-input" rows={4} placeholder={editItem ? "Enter new content to replace existing, or leave blank to keep current content." : "Enter your sensitive text content here... (Optional if attaching a file)"}
                  {...register('content')} />
                {errors.content && <span className="form-error">{errors.content.message}</span>}
              </div>

              {!editItem && (
                <div className="form-group">
                  <label className="form-label">Attach File (Optional, max 10MB)</label>
                  <input type="file" className="form-input" style={{ padding: '8px' }} {...register('file')} />
                </div>
              )}

              <div className="form-group">
                <label className="form-label">Vault Password (used for encryption)</label>
                <div className="input-with-icon" style={{ position: 'relative' }}>
                  <input
                    className="form-input" placeholder="Your account password" style={{ paddingRight: 44 }}
                    type={showVaultPw ? 'text' : 'password'}
                    value={vaultPassword} onChange={e => setVaultPassword(e.target.value)}
                  />
                  <button type="button" style={{ position: 'absolute', right: 12, top: '50%', transform: 'translateY(-50%)', background: 'none', border: 'none', color: 'var(--text-muted)', display: 'flex' }}
                    onClick={() => setShowVaultPw(!showVaultPw)}>
                    {showVaultPw ? <EyeOff size={16} /> : <Eye size={16} />}
                  </button>
                </div>
              </div>

              {recipients.length > 0 && (
                <div className="form-group">
                  <label className="form-label">Assign Recipients</label>
                  <div className="recipient-checkboxes">
                    {recipients.map(r => (
                      <label key={r.id} className="recipient-checkbox-label">
                        <input type="checkbox" value={r.id} {...register('recipientIds')} />
                        <span>{r.name} ({r.email})</span>
                      </label>
                    ))}
                  </div>
                </div>
              )}

              <div className="flex gap-2" style={{ justifyContent: 'flex-end', marginTop: 8 }}>
                <button type="button" className="btn btn-ghost" onClick={() => { setShowForm(false); setEditItem(null); reset(); }}>Cancel</button>
                <button type="submit" className="btn btn-primary btn-ripple" disabled={createMutation.isPending || updateMutation.isPending}>
                  {(createMutation.isPending || updateMutation.isPending) ? <span className="spinner" /> : <><Lock size={14} /> {editItem ? 'Update & Encrypt' : 'Encrypt & Store'}</>}
                </button>
              </div>
            </form>
            </div>
          </motion.div>
        )}
      </AnimatePresence>

      {/* Items Grid */}
      {isLoading ? (
        <div className="vault-grid" style={{ paddingTop: 20 }}>
          <div className="skeleton-shimmer h-32 w-full"></div>
          <div className="skeleton-shimmer h-32 w-full"></div>
          <div className="skeleton-shimmer h-32 w-full"></div>
        </div>
      ) : items.length === 0 ? (
        <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="empty-state">
          <Lock size={48} className="empty-icon" />
          <h3>Your vault is empty</h3>
          <p className="text-muted text-sm">Add your first encrypted item to get started.</p>
        </motion.div>
      ) : (
        <motion.div 
          className="vault-grid stagger-children"
          initial="hidden" animate="show"
          variants={{
            hidden: { opacity: 0 },
            show: { opacity: 1, transition: { staggerChildren: 0.1 } }
          }}
        >
          {items.map(item => {
            const ItemIcon = contentTypeIcons[item.contentType] || FileText;
            return (
              <motion.div 
                variants={{
                  hidden: { opacity: 0, y: 20 },
                  show: { opacity: 1, y: 0, transition: { type: 'spring', stiffness: 100 } }
                }}
                key={item.id} className="card glass-panel vault-item-card"
              >
                <div className="vault-item-header">
                  <div className="vault-item-icon">
                    <ItemIcon size={18} />
                  </div>
                  <div className="vault-item-type">{contentTypeLabels[item.contentType]}</div>
                </div>
                <h3 className="vault-item-label">{item.label}</h3>
                <div className="vault-item-meta" style={{ display: 'flex', gap: 12, alignItems: 'center' }}>
                  <span>{item.recipients?.length || 0} recipients</span>
                  {item.hasFile && <span style={{ display: 'flex', alignItems: 'center', gap: 4, color: 'var(--primary-light)' }}><Paperclip size={12} /> Attachment</span>}
                </div>
                <div className="vault-item-actions">
                  <button className="btn btn-ghost btn-icon" onClick={() => setViewItem(item)} title="View Item">
                    <Eye size={16} />
                  </button>
                  <button className="btn btn-ghost btn-icon" onClick={() => { 
                    setEditItem(item); 
                    reset({ 
                      label: item.label, 
                      contentType: item.contentType, 
                      content: '',
                      recipientIds: item.recipients.map(r => r.id)
                    });
                    window.scrollTo({ top: 0, behavior: 'smooth' });
                  }} title="Edit Item">
                    <Edit2 size={16} />
                  </button>
                  <button className="btn btn-ghost btn-icon delete-btn" 
                    onClick={() => {
                      setItemToDelete(item);
                      setDeleteVaultPassword('');
                    }} title="Delete Item">
                    <Trash2 size={16} />
                  </button>
                </div>
              </motion.div>
            );
          })}
        </motion.div>
      )}

      {/* View / Decrypt Modal */}
      {viewItem && (
        <div className="modal-overlay vault-view-modal" onClick={(e) => e.target === e.currentTarget && handleCloseViewModal()}>
          <div className="modal-card animate-scale-in">
            <div className="modal-header">
              <div>
                <h3 style={{ marginBottom: 4 }}>🔓 Decrypt Item</h3>
                <p className="text-muted text-sm">{viewItem.label}</p>
              </div>
              <button className="modal-close" onClick={handleCloseViewModal}><X size={18} /></button>
            </div>

            <div className="vault-decrypt-section">
              <div className="form-group">
                <label className="form-label">Vault Password</label>
                <div className="vault-password-row">
                  <input
                    id="view-vault-password"
                    className="form-input"
                    type={showViewPw ? 'text' : 'password'}
                    placeholder="Enter your vault password"
                    value={viewPassword}
                    onChange={e => { setViewPassword(e.target.value); setDecryptedContent(null); }}
                    onKeyDown={e => e.key === 'Enter' && handleDecrypt()}
                  />
                  <button
                    type="button"
                    className="btn btn-ghost"
                    style={{ flexShrink: 0 }}
                    onClick={() => setShowViewPw(!showViewPw)}
                  >
                    {showViewPw ? <EyeOff size={16} /> : <Eye size={16} />}
                  </button>
                </div>
              </div>

              <div style={{ display: 'flex', gap: '8px' }}>
                <button
                  id="decrypt-btn"
                  className="btn btn-primary btn-ripple w-full"
                  onClick={handleDecrypt}
                  disabled={isDecrypting || !viewPassword}
                >
                  {isDecrypting ? <><span className="spinner" /> Decrypting…</> : <><Lock size={14} /> Decrypt & View</>}
                </button>
              </div>
            </div>

            {(decryptedContent !== null || decryptedFileUrl !== null) && (
              <div className="decrypted-content animate-fade-in" style={{ marginTop: 20 }}>
                {decryptedContent && (
                  <>
                    <div className="decrypted-header">
                      <ShieldAlert size={14} color="var(--primary)" />
                      <span className="text-sm font-semibold">Decrypted Text Content</span>
                    </div>
                    <div className="decrypted-body" style={{ marginBottom: 16 }}>
                      {decryptedContent.split('\n').map((line, i) => <p key={i}>{line}</p>)}
                    </div>
                    <button className="btn btn-ghost btn-sm" onClick={handleCopy}>
                      {copied ? <><Check size={14} /> Copied</> : <><Copy size={14} /> Copy Text</>}
                    </button>
                  </>
                )}
                
                {decryptedFileUrl && (
                  <div style={{ marginTop: decryptedContent ? 24 : 0 }}>
                    <div className="decrypted-header" style={{ marginBottom: 12 }}>
                      <ShieldAlert size={14} color="var(--primary)" />
                      <span className="text-sm font-semibold">Decrypted File: {viewItem.originalFileName}</span>
                    </div>
                    
                    {fileType === 'image' && (
                      <img src={decryptedFileUrl} alt="Decrypted" style={{ maxWidth: '100%', borderRadius: 8, border: '1px solid var(--border)' }} />
                    )}
                    {fileType === 'pdf' && (
                      <iframe src={decryptedFileUrl} title="Decrypted PDF" width="100%" height="500px" style={{ border: '1px solid var(--border)', borderRadius: 8 }} />
                    )}
                    {fileType === 'text' && decryptedFileText !== null && (
                      <div className="decrypted-body" style={{ marginTop: 12, padding: 16, background: 'var(--bg-card-hover)', border: '1px solid var(--border)', borderRadius: 8, whiteSpace: 'pre-wrap', maxHeight: '400px', overflowY: 'auto' }}>
                        {decryptedFileText}
                      </div>
                    )}
                    {fileType === 'other' && (
                      <div style={{ display: 'flex', alignItems: 'center', gap: 12, padding: 16, background: 'var(--bg-card-hover)', borderRadius: 8, border: '1px solid var(--border)' }}>
                         <span style={{ flex: 1, fontWeight: 500 }}>{viewItem.originalFileName}</span>
                         <a href={decryptedFileUrl} download={viewItem.originalFileName} className="btn btn-primary btn-sm" style={{ textDecoration: 'none' }}>Download to View</a>
                      </div>
                    )}
                  </div>
                )}
              </div>
            )}
            <div className="vault-security-note" style={{ marginTop: 16 }}>
              <ShieldAlert size={12} />
              Content is cleared from memory when you close this dialog
            </div>
          </div>
        </div>
      )}

      {/* Delete Confirmation Modal */}
      {itemToDelete && (
        <div className="modal-overlay vault-view-modal" onClick={(e) => e.target === e.currentTarget && setItemToDelete(null)}>
          <div className="modal-card animate-scale-in" style={{ maxWidth: 400 }}>
            <div className="modal-header">
              <div>
                <h3 className="text-danger" style={{ marginBottom: 4 }}>🗑️ Delete Item</h3>
                <p className="text-muted text-sm">Delete "{itemToDelete.label}"</p>
              </div>
              <button className="modal-close" onClick={() => setItemToDelete(null)}><X size={18} /></button>
            </div>
            <div className="vault-decrypt-section" style={{ borderTop: 'none', paddingTop: 0 }}>
              <p className="text-sm mb-4">Please enter your login password to confirm deletion. This action cannot be undone.</p>
              <div className="form-group mb-4">
                <input
                  type="password"
                  className="form-input"
                  placeholder="Login Password"
                  value={deleteVaultPassword}
                  onChange={e => setDeleteVaultPassword(e.target.value)}
                  onKeyDown={e => e.key === 'Enter' && deleteMutation.mutate({ id: itemToDelete.id, password: deleteVaultPassword })}
                />
                <div style={{ textAlign: 'right', marginTop: 8 }}>
                  <span className="text-xs text-primary" style={{ cursor: 'pointer' }} onClick={() => toast('To reset your password, log out and click Forgot Password.', { icon: 'ℹ️' })}>
                    Forgot password?
                  </span>
                </div>
              </div>
              <div style={{ display: 'flex', gap: '8px', justifyContent: 'flex-end' }}>
                <button className="btn btn-ghost" onClick={() => setItemToDelete(null)} disabled={deleteMutation.isPending}>Cancel</button>
                <button
                  className="btn btn-danger"
                  onClick={() => deleteMutation.mutate({ id: itemToDelete.id, password: deleteVaultPassword })}
                  disabled={deleteMutation.isPending || !deleteVaultPassword}
                >
                  {deleteMutation.isPending ? <span className="spinner" /> : 'Delete Permanently'}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

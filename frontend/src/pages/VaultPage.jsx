import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { vaultApi, recipientApi } from '../api/client';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import {
  Plus, Lock, Eye, EyeOff, Trash2, FileText, Key, MessageSquare, AlertCircle,
  X, Copy, Check, ShieldAlert, Paperclip, Edit2, ShieldCheck
} from 'lucide-react';
import toast from 'react-hot-toast';
import { motion, AnimatePresence } from 'framer-motion';
import './VaultPage.css';

// Zero-knowledge crypto module — all crypto stays in the browser
import {
  generateSalt,
  generateDEK,
  deriveKey,
  encryptDEK,
  decryptDEK,
  encryptContent,
  decryptContent,
  encryptFile,
  decryptFile,
  exportDEKRaw,
} from '../crypto/vault.js';

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
  const [showForm, setShowForm]     = useState(false);
  const [editItem, setEditItem]     = useState(null);
  const [viewItem, setViewItem]     = useState(null);
  const [vaultPassword, setVaultPassword] = useState('');
  const [showVaultPw, setShowVaultPw]     = useState(false);

  // View modal state
  const [viewPassword, setViewPassword]       = useState('');
  const [showViewPw, setShowViewPw]           = useState(false);
  const [decryptedContent, setDecryptedContent] = useState(null);
  const [copied, setCopied]                   = useState(false);
  const [decryptedFileUrl, setDecryptedFileUrl] = useState(null);
  const [fileType, setFileType]               = useState(null);
  const [decryptedFileText, setDecryptedFileText] = useState(null);
  const [isDecrypting, setIsDecrypting]       = useState(false);
  const [isEncrypting, setIsEncrypting]       = useState(false);

  // Delete modal state
  const [itemToDelete, setItemToDelete]           = useState(null);
  const [deleteVaultPassword, setDeleteVaultPassword] = useState('');

  const { data: itemsRes, isLoading } = useQuery({
    queryKey: ['vault-items'],
    queryFn: vaultApi.list,
  });
  const { data: recipientsRes } = useQuery({
    queryKey: ['recipients'],
    queryFn: recipientApi.list,
  });

  const items      = itemsRes?.data?.data || [];
  const recipients = recipientsRes?.data?.data || [];

  const { register, handleSubmit, reset, formState: { errors } } = useForm();

  // ==================== Mutations ====================

  const createMutation = useMutation({
    mutationFn: (encryptedPayload) => vaultApi.create(encryptedPayload),
    onSuccess: () => {
      queryClient.invalidateQueries(['vault-items']);
      toast.success('Item encrypted and stored in your vault');
      setShowForm(false);
      setVaultPassword('');
      reset();
    },
    onError: (e) => toast.error(e.response?.data?.message || 'Failed to store item'),
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, payload }) => vaultApi.update(id, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['vault-items'] });
      setEditItem(null);
      setVaultPassword('');
      toast.success('Vault item updated');
      reset();
    },
    onError: (err) => toast.error(err.response?.data?.message || 'Failed to update item'),
  });

  const deleteMutation = useMutation({
    mutationFn: (id) => vaultApi.delete(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['vault-items'] });
      toast.success('Item deleted');
      setItemToDelete(null);
      setDeleteVaultPassword('');
    },
    onError: (e) => toast.error('Failed to delete: ' + (e.response?.data?.message || 'error')),
  });

  // ==================== View Modal Handlers ====================

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

  /**
   * Decrypts a vault item entirely in the browser using the WebCrypto API.
   * The vault password never leaves this function — only the CryptoKey derived from it
   * is used, and it cannot be serialised back to bytes.
   */
  const handleDecrypt = async () => {
    if (!viewPassword) { toast.error('Please enter your vault password'); return; }
    setIsDecrypting(true);
    try {
      // Step 1 — fetch the encrypted blob from the server (no password sent)
      const res = await vaultApi.get(viewItem.id);
      const item = res.data.data;

      // Step 2 — re-derive the user master key from password + stored salt (entirely in browser)
      const masterKey = await deriveKey(viewPassword, item.salt);

      // Step 3 — unwrap the DEK using the master key (GCM tag failure = wrong password)
      const dek = await decryptDEK(item.encryptedDEK, item.dekIv, masterKey);

      // Step 4 — decrypt content
      if (item.ciphertext && item.iv) {
        const plaintext = await decryptContent(item.ciphertext, item.iv, dek);
        setDecryptedContent(plaintext);
      }

      // Step 5 — decrypt file attachment if present
      if (item.fileCiphertext && item.fileIv) {
        const decryptedBuffer = await decryptFile(item.fileCiphertext, item.fileIv, dek);
        const name = (item.originalFileName || '').toLowerCase();
        let mimeType = 'application/octet-stream';
        if (name.endsWith('.pdf'))                                      mimeType = 'application/pdf';
        else if (name.endsWith('.png'))                                 mimeType = 'image/png';
        else if (name.endsWith('.jpg') || name.endsWith('.jpeg'))       mimeType = 'image/jpeg';
        else if (name.endsWith('.gif'))                                 mimeType = 'image/gif';
        else if (name.endsWith('.webp'))                                mimeType = 'image/webp';
        else if (name.endsWith('.txt') || name.endsWith('.csv') || name.endsWith('.md')) mimeType = 'text/plain';

        const blob = new Blob([decryptedBuffer], { type: mimeType });
        const url  = URL.createObjectURL(blob);
        setDecryptedFileUrl(url);

        if (['png','jpg','jpeg','gif','webp'].some(ext => name.endsWith('.' + ext))) {
          setFileType('image');
        } else if (name.endsWith('.pdf')) {
          setFileType('pdf');
        } else if (name.endsWith('.txt') || name.endsWith('.csv') || name.endsWith('.md')) {
          setFileType('text');
          setDecryptedFileText(await blob.text());
        } else {
          setFileType('other');
        }
      }

      toast.success('Decrypted — content visible only in your browser');
    } catch (e) {
      console.error('Decryption failed:', e);
      // DOMException from GCM tag failure → wrong password
      toast.error('Wrong vault password or corrupted data');
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

  // ==================== Form Submit (Create / Update) ====================

  /**
   * Encrypts content and file in the browser before sending to the server.
   * The vault password never leaves this function.
   */
  const onSubmit = async (data) => {
    if (!vaultPassword) { toast.error('Vault password is required to encrypt content'); return; }
    if (!data.content && (!data.file || data.file.length === 0)) {
      toast.error('Either text content or a file attachment is required');
      return;
    }

    setIsEncrypting(true);
    try {
      let rIds = data.recipientIds;
      if (!rIds) rIds = [];
      else if (!Array.isArray(rIds)) rIds = [rIds];

      if (editItem) {
        // ---- UPDATE PATH ----
        // Fetch the existing encrypted blob to re-use the existing DEK
        const existingRes = await vaultApi.get(editItem.id);
        const existing = existingRes.data.data;

        // Re-derive master key and unwrap existing DEK
        const masterKey = await deriveKey(vaultPassword, existing.salt);
        const dek = await decryptDEK(existing.encryptedDEK, existing.dekIv, masterKey);

        let ciphertext = existing.ciphertext;
        let iv         = existing.iv;

        if (data.content && data.content.trim()) {
          const encrypted = await encryptContent(data.content, dek);
          ciphertext = encrypted.ciphertext;
          iv         = encrypted.iv;
        }

        const payload = {
          label:        data.label,
          contentType:  data.contentType,
          ciphertext,
          iv,
          encryptedDEK: existing.encryptedDEK,
          dekIv:        existing.dekIv,
          salt:         existing.salt,
          recipientIds: rIds.filter(Boolean),
        };

        // Also re-export raw DEK so server can re-wrap it for release path
        const rawDEK = await exportDEKRaw(dek);
        payload.rawDEK = rawDEK;

        updateMutation.mutate({ id: editItem.id, payload });
        return;
      }

      // ---- CREATE PATH ----
      // Generate a fresh salt, derive master key, generate a fresh DEK
      const salt      = generateSalt();
      const masterKey = await deriveKey(vaultPassword, salt);
      const dek       = await generateDEK();

      // Wrap DEK with user master key (stored in DB — only user can unwrap)
      const { encryptedDEK, dekIv } = await encryptDEK(dek, masterKey);

      // Export raw DEK bytes — server wraps these with server key for the release path,
      // then the raw bytes are discarded. Sent over HTTPS; never stored raw server-side.
      const rawDEK = await exportDEKRaw(dek);

      let ciphertext = null;
      let contentIv  = null;
      if (data.content && data.content.trim()) {
        const encrypted = await encryptContent(data.content, dek);
        ciphertext = encrypted.ciphertext;
        contentIv  = encrypted.iv;
      }

      let fileCiphertext  = null;
      let fileIv          = null;
      let originalFileName = null;
      if (data.file && data.file.length > 0) {
        const file        = data.file[0];
        originalFileName  = file.name;
        const fileBuffer  = await file.arrayBuffer();
        const encrypted   = await encryptFile(fileBuffer, dek);
        fileCiphertext    = encrypted.ciphertext;
        fileIv            = encrypted.iv;
      }

      createMutation.mutate({
        label:          data.label,
        contentType:    data.contentType,
        ciphertext,
        iv:             contentIv,
        encryptedDEK,
        dekIv,
        salt,
        rawDEK,         // sent once, used server-side for release-path DEK wrapping only
        fileCiphertext,
        fileIv,
        originalFileName,
        recipientIds:   rIds.filter(Boolean),
      });
    } catch (e) {
      console.error('Encryption failed:', e);
      toast.error('Encryption failed — please try again');
    } finally {
      setIsEncrypting(false);
    }
  };

  // ==================== Delete Handler ====================

  const handleDelete = async () => {
    if (!deleteVaultPassword) { toast.error('Enter your vault password to confirm'); return; }
    // Verify password client-side before sending delete request
    try {
      const res = await vaultApi.get(itemToDelete.id);
      const item = res.data.data;
      const masterKey = await deriveKey(deleteVaultPassword, item.salt);
      // Unwrap DEK — if password is wrong, this throws. Plaintext is never sent to server.
      await decryptDEK(item.encryptedDEK, item.dekIv, masterKey);
      // Password confirmed — proceed with delete
      deleteMutation.mutate(itemToDelete.id);
    } catch {
      toast.error('Wrong vault password — cannot delete');
    }
  };

  // ==================== Render ====================

  return (
    <div className="page-container">
      <div className="page-header">
        <div>
          <h2>🔐 Vault</h2>
          <p className="text-muted text-sm" style={{ marginTop: 4, display: 'flex', alignItems: 'center', gap: 6 }}>
            <ShieldCheck size={14} color="var(--primary)" />
            AES-256-GCM encryption — your vault password never leaves this browser
          </p>
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
                  <label className="form-label">Content (encrypted in your browser before upload)</label>
                  <textarea className="form-input" rows={4}
                    placeholder={editItem
                      ? 'Enter new content to replace existing, or leave blank to keep current content.'
                      : 'Enter your sensitive text content here… (Optional if attaching a file)'}
                    {...register('content')} />
                  {errors.content && <span className="form-error">{errors.content.message}</span>}
                </div>

                {!editItem && (
                  <div className="form-group">
                    <label className="form-label">Attach File (Optional, max 10MB — encrypted in browser)</label>
                    <input type="file" className="form-input" style={{ padding: '8px' }} {...register('file')} />
                  </div>
                )}

                <div className="form-group">
                  <label className="form-label">Vault Password <span style={{ color: 'var(--text-muted)', fontWeight: 400 }}>(never sent to server — used only for local key derivation)</span></label>
                  <div className="input-with-icon" style={{ position: 'relative' }}>
                    <input
                      className="form-input" placeholder="Your vault password" style={{ paddingRight: 44 }}
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
                  <button type="submit" className="btn btn-primary btn-ripple"
                    disabled={createMutation.isPending || updateMutation.isPending || isEncrypting}>
                    {(createMutation.isPending || updateMutation.isPending || isEncrypting)
                      ? <><span className="spinner" /> Encrypting…</>
                      : <><Lock size={14} /> {editItem ? 'Update & Re-Encrypt' : 'Encrypt & Store'}</>}
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
                      recipientIds: item.recipients.map(r => r.id),
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

              <button
                id="decrypt-btn"
                className="btn btn-primary btn-ripple w-full"
                onClick={handleDecrypt}
                disabled={isDecrypting || !viewPassword}
              >
                {isDecrypting ? <><span className="spinner" /> Decrypting in browser…</> : <><Lock size={14} /> Decrypt & View</>}
              </button>
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
              Decryption happens entirely in your browser — plaintext never reaches the server
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
              <p className="text-sm mb-4">Enter your vault password to confirm deletion. This action cannot be undone.</p>
              <div className="form-group mb-4">
                <input
                  type="password"
                  className="form-input"
                  placeholder="Vault Password"
                  value={deleteVaultPassword}
                  onChange={e => setDeleteVaultPassword(e.target.value)}
                  onKeyDown={e => e.key === 'Enter' && handleDelete()}
                />
              </div>
              <div style={{ display: 'flex', gap: '8px', justifyContent: 'flex-end' }}>
                <button className="btn btn-ghost" onClick={() => setItemToDelete(null)} disabled={deleteMutation.isPending}>Cancel</button>
                <button
                  className="btn btn-danger"
                  onClick={handleDelete}
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

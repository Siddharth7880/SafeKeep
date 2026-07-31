import { useQuery } from '@tanstack/react-query';
import { auditApi } from '../api/client';
import { format } from 'date-fns';
import { ScrollText, CheckCircle, LogIn, Lock, User, AlertTriangle, Shield, Terminal } from 'lucide-react';
import { motion } from 'framer-motion';

const eventIcons = {
  CHECKIN: CheckCircle,
  LOGIN: LogIn,
  REGISTER: User,
  VAULT_ITEM_CREATED: Lock,
  VAULT_ITEM_DELETED: Lock,
  RECIPIENT_ADDED: User,
  STATUS_TRANSITION: AlertTriangle,
  CONTENT_RELEASED: Shield,
  REMINDER_SENT: AlertTriangle,
  SETTINGS_UPDATED: Shield,
};

const eventColors = {
  CHECKIN: 'var(--success)',
  LOGIN: 'var(--primary-light)',
  STATUS_TRANSITION: 'var(--warning)',
  CONTENT_RELEASED: 'var(--danger)',
  REMINDER_SENT: 'var(--warning)',
};

export default function AuditLogPage() {
  const { data: res, isLoading } = useQuery({
    queryKey: ['audit-logs'],
    queryFn: () => auditApi.getLogs(0, 50),
  });

  const logs = res?.data?.data?.content || [];

  return (
    <div className="page-container">
      <div className="page-header">
        <div>
          <h2 className="flex items-center gap-3"><Terminal className="text-primary" /> Audit Log</h2>
          <p className="text-muted text-sm mt-1">Immutable record of all system events. Cannot be deleted or modified.</p>
        </div>
      </div>

      {isLoading ? (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12, paddingTop: 20 }}>
          <div className="skeleton-shimmer h-16 w-full"></div>
          <div className="skeleton-shimmer h-16 w-full"></div>
          <div className="skeleton-shimmer h-16 w-full"></div>
        </div>
      ) : logs.length === 0 ? (
        <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="empty-state">
          <ScrollText size={48} className="empty-icon" />
          <h3>No audit events yet</h3>
          <p className="text-muted text-sm">Events will appear here as you use SafeKeep.</p>
        </motion.div>
      ) : (
        <motion.div 
          className="card glass-panel"
          initial="hidden" animate="show"
          variants={{
            hidden: { opacity: 0 },
            show: { opacity: 1, transition: { staggerChildren: 0.05 } }
          }}
        >
          <div style={{ display: 'flex', flexDirection: 'column' }}>
            {logs.map((log, idx) => {
              const Icon = eventIcons[log.eventType] || Shield;
              const color = eventColors[log.eventType] || 'var(--text-secondary)';
              return (
                <motion.div 
                  key={log.id} 
                  variants={{
                    hidden: { opacity: 0, x: -10 },
                    show: { opacity: 1, x: 0 }
                  }}
                  style={{ display: 'flex', gap: 16, padding: '14px 0', borderBottom: idx < logs.length - 1 ? '1px solid var(--border)' : 'none' }}
                >
                  <div style={{ width: 36, height: 36, borderRadius: '50%', background: `${color}18`, border: `1px solid ${color}30`, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0, marginTop: 2 }}>
                    <Icon size={16} style={{ color }} />
                  </div>
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 8 }}>
                      <div style={{ fontWeight: 600, fontSize: 13 }}>{log.eventType.replace(/_/g, ' ')}</div>
                      <div className="text-xs text-muted" style={{ flexShrink: 0 }}>
                        {log.createdAt ? format(new Date(log.createdAt), 'MMM d, HH:mm') : '—'}
                      </div>
                    </div>
                    {log.details && <div className="text-sm text-muted" style={{ marginTop: 2 }}>{log.details}</div>}
                    {log.previousStatus && (
                      <div className="text-xs" style={{ marginTop: 4, color: 'var(--text-muted)' }}>
                        {log.previousStatus} → {log.newStatus}
                        {log.triggeredBy && ` · by ${log.triggeredBy}`}
                      </div>
                    )}
                  </div>
                </motion.div>
              );
            })}
          </div>
        </motion.div>
      )}
    </div>
  );
}

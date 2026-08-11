import { useState, useEffect, useMemo } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { checkinApi, authApi, auditApi } from '../api/client';
import { useAuthStore } from '../store/authStore';
import {
  Shield, Clock, CheckCircle, AlertTriangle, XCircle, Pause, Play,
  TrendingUp, Calendar, Settings, Lock, Users, Zap, Info
} from 'lucide-react';
import { formatDistanceToNow, format } from 'date-fns';
import toast from 'react-hot-toast';
import { motion, AnimatePresence } from 'framer-motion';
import './DashboardPage.css';

/* ---- Status Config ---- */
const statusConfig = {
  ACTIVE:         { label: 'Active',          icon: CheckCircle,   cls: 'badge-active',   color: '#10b981', ringClass: '' },
  MISSED_CHECKIN: { label: 'Missed Check-In', icon: AlertTriangle, cls: 'badge-warning',  color: '#f59e0b', ringClass: 'warning' },
  GRACE_PERIOD:   { label: 'Grace Period',    icon: AlertTriangle, cls: 'badge-danger',   color: '#ef4444', ringClass: 'danger' },
  RELEASED:       { label: 'Released',        icon: XCircle,       cls: 'badge-released', color: '#a78bfa', ringClass: 'danger' },
  PAUSED:         { label: 'Paused',          icon: Pause,         cls: 'badge-paused',   color: '#94a3b8', ringClass: '' },
};

/* ---- Sliding Digit ---- */
function SlidingDigit({ value, label }) {
  return (
    <div className="digit-group">
      <div className="digit-box">
        <AnimatePresence mode="popLayout">
          <motion.span
            key={value}
            initial={{ y: 18, opacity: 0 }}
            animate={{ y: 0, opacity: 1 }}
            exit={{ y: -18, opacity: 0 }}
            transition={{ type: 'spring', stiffness: 280, damping: 22 }}
            className="digit"
          >
            {value}
          </motion.span>
        </AnimatePresence>
      </div>
      {label && <span className="digit-label">{label}</span>}
    </div>
  );
}

/* ---- Security Score Ring ---- */
function SecurityScoreRing({ score, checkinCount, streakDays, intervalDays }) {
  const r = 36;
  const circ = 2 * Math.PI * r;
  const offset = circ - (score / 100) * circ;
  const ringClass = score >= 75 ? '' : score >= 45 ? 'warning' : 'danger';

  return (
    <div className="security-score-wrap">
      <div className="score-ring-container">
        <svg className="score-ring-svg" viewBox="0 0 90 90" width="90" height="90">
          <circle className="score-ring-track" cx="45" cy="45" r={r} />
          <circle
            className={`score-ring-fill ${ringClass}`}
            cx="45" cy="45" r={r}
            strokeDasharray={circ}
            strokeDashoffset={offset}
          />
        </svg>
        <div className="score-center-text">
          <span className="score-number">{score}</span>
          <span className="score-label-small">SCORE</span>
        </div>
      </div>
      <div className="score-details">
        <div className="score-metric">
          <span className="score-metric-label">Check-in Consistency</span>
          <div className="score-metric-bar">
            <div
              className={`score-metric-fill ${checkinCount < 5 ? 'warn' : ''}`}
              style={{ width: `${Math.min(100, (checkinCount / 20) * 100)}%` }}
            />
          </div>
        </div>
        <div className="score-metric">
          <span className="score-metric-label">Current Streak</span>
          <div className="score-metric-bar">
            <div
              className={`score-metric-fill ${streakDays < 3 ? 'warn' : ''}`}
              style={{ width: `${Math.min(100, (streakDays / 14) * 100)}%` }}
            />
          </div>
        </div>
        <div className="score-metric">
          <span className="score-metric-label">Interval Coverage</span>
          <div className="score-metric-bar">
            <div
              className="score-metric-fill"
              style={{ width: `${Math.min(100, (intervalDays / 7) * 100)}%` }}
            />
          </div>
        </div>
      </div>
    </div>
  );
}

/* ---- Main Dashboard ---- */
export default function DashboardPage() {
  const queryClient = useQueryClient();
  const updateUser = useAuthStore((s) => s.updateUser);

  const { data: profileRes, isLoading } = useQuery({
    queryKey: ['profile'],
    queryFn: authApi.getProfile,
    refetchInterval: 30000,
  });

  const checkinMutation = useMutation({
    mutationFn: checkinApi.perform,
    onSuccess: (res) => {
      const user = res.data?.data;
      if (user) { updateUser(user); queryClient.invalidateQueries({ queryKey: ['profile'] }); }
      toast.success('✅ Check-in successful! Timer reset.');
    },
    onError: () => toast.error('Check-in failed. Please try again.'),
  });

  const pauseMutation = useMutation({
    mutationFn: checkinApi.pauseSwitch,
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['profile'] }); toast.success('Switch paused.'); },
  });

  const resumeMutation = useMutation({
    mutationFn: checkinApi.resumeSwitch,
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['profile'] }); toast.success('Switch resumed!'); },
  });

  const profile = profileRes?.data?.data;
  const status = profile?.status || 'ACTIVE';
  const cfg = statusConfig[status] || statusConfig.ACTIVE;
  const Icon = cfg.icon;
  const deadlineString = profile?.nextCheckinDeadline;
  const deadline = deadlineString ? new Date(deadlineString) : null;
  const daysLeft = profile?.daysUntilDeadline ?? 0;
  const isOverdue = profile?.isOverdue;
  const isReleased = status === 'RELEASED';
  const isPaused = status === 'PAUSED';

  // Security score calculation
  const securityScore = useMemo(() => {
    if (!profile) return 72;
    let score = 50;
    if (status === 'ACTIVE') score += 20;
    else if (status === 'PAUSED') score += 10;
    else if (status === 'MISSED_CHECKIN') score -= 10;
    else if (status === 'GRACE_PERIOD') score -= 25;
    score += Math.min(20, (profile.checkinCount || 0) * 2);
    score += Math.min(10, (profile.streakDays || 0));
    return Math.min(100, Math.max(0, score));
  }, [profile, status]);

  // Live countdown timer
  const [timeLeft, setTimeLeft] = useState({ h: '', m: '', s: '' });
  useEffect(() => {
    if (!deadlineString || isOverdue || isReleased || isPaused) return;
    const deadlineDate = new Date(deadlineString);
    const tick = () => {
      const diff = deadlineDate - new Date();
      if (diff > 0 && diff <= 24 * 3600 * 1000) {
        setTimeLeft({
          h: String(Math.floor(diff / 3600000)).padStart(2, '0'),
          m: String(Math.floor((diff % 3600000) / 60000)).padStart(2, '0'),
          s: String(Math.floor((diff % 60000) / 1000)).padStart(2, '0'),
        });
      } else {
        setTimeLeft({ h: '', m: '', s: '' });
      }
    };
    tick();
    const id = setInterval(tick, 1000);
    return () => clearInterval(id);
  }, [deadlineString, isOverdue, isReleased, isPaused]);

  // Audit logs
  const { data: logsRes, isLoading: logsLoading } = useQuery({
    queryKey: ['auditLogs'],
    queryFn: () => auditApi.getLogs(0, 50),
  });

  if (isLoading || logsLoading) return (
    <div style={{ padding: 32, display: 'flex', flexDirection: 'column', gap: 20 }}>
      {[140, 200, 120].map((h, i) => (
        <div key={i} className="skeleton-shimmer" style={{ height: h, borderRadius: 20 }} />
      ))}
    </div>
  );

  const parseLogDate = (d) => {
    if (!d) return null;
    try {
      if (Array.isArray(d)) return new Date(d[0], d[1] - 1, d[2], d[3] || 0, d[4] || 0, d[5] || 0);
      const dt = new Date(d);
      return isNaN(dt.getTime()) ? null : dt;
    } catch { return null; }
  };

  const logs = logsRes?.data?.data?.content || [];

  // Heatmap — last 28 days
  const heatmapData = Array.from({ length: 28 }).map((_, i) => {
    const d = new Date(Date.now() - (27 - i) * 86400000);
    const dateStr = d.toISOString().split('T')[0];
    const hasCheckin = logs.some(l => {
      const ld = parseLogDate(l.createdAt);
      return l.eventType === 'CHECKIN' && ld && ld.toISOString().split('T')[0] === dateStr;
    });
    return { date: d, active: hasCheckin };
  });
  const daysOfWeek = Array.from({ length: 7 }).map((_, i) =>
    ['S', 'M', 'T', 'W', 'T', 'F', 'S'][heatmapData[i].date.getDay()]
  );
  const activeCount = heatmapData.filter(d => d.active).length;

  // Activity feed
  const activityFeed = logs.slice(0, 6).map(log => {
    const et = log.eventType || '';
    let iconType = 'default';
    if (et === 'CHECKIN') iconType = 'checkin';
    else if (et.includes('VAULT')) iconType = 'vault';
    else if (et.includes('SETTINGS')) iconType = 'settings';
    const logDate = parseLogDate(log.createdAt);
    let timeStr = '—';
    try { if (logDate) timeStr = formatDistanceToNow(logDate, { addSuffix: true }); } catch {}
    return { id: log.id, iconType, title: log.details || et.replace(/_/g, ' '), time: timeStr };
  });

  const containerVariants = {
    hidden: { opacity: 0 },
    show: { opacity: 1, transition: { staggerChildren: 0.08 } }
  };
  const itemVariants = {
    hidden: { opacity: 0, y: 22 },
    show: { opacity: 1, y: 0, transition: { type: 'spring', damping: 22, stiffness: 120 } }
  };

  const liveDotClass = status === 'GRACE_PERIOD' || status === 'MISSED_CHECKIN' ? 'danger'
    : status === 'PAUSED' ? '' : '';

  return (
    <motion.div
      className="dashboard-content"
      variants={containerVariants}
      initial="hidden"
      animate="show"
    >
      {/* Ambient BG */}
      <div className={`dashboard-ambient-bg ambient-${status}`} />

      {/* ── Released State ── */}
      {isReleased ? (
        <motion.div className="bento-panel released-card" variants={itemVariants}>
          <div className="released-icon">📬</div>
          <h2 className="text-danger">Vault Released</h2>
          <p className="text-muted" style={{ marginTop: 8, maxWidth: 480 }}>
            Your vault contents were permanently released to your designated recipients on{' '}
            <strong style={{ color: 'var(--text-primary)' }}>
              {profile?.releasedAt ? format(new Date(profile.releasedAt), 'MMMM d, yyyy') : '—'}
            </strong>.
          </p>
        </motion.div>
      ) : (
        <>
          {/* ── Row 1: Stats Bar ── */}
          <motion.div variants={itemVariants} className="stats-bar">
            <div className="stat-pill">
              <div className={`live-dot ${liveDotClass}`} />
              <Icon size={14} style={{ color: cfg.color }} />
              <span>{cfg.label}</span>
            </div>
            <div className="stat-pill">
              <TrendingUp size={14} style={{ color: 'var(--success)' }} />
              <span>Streak</span>
              <span className="stat-pill-value">{profile?.streakDays || 0}d</span>
            </div>
            <div className="stat-pill">
              <CheckCircle size={14} style={{ color: '#818cf8' }} />
              <span>Check-ins</span>
              <span className="stat-pill-value">{profile?.checkinCount || 0}</span>
            </div>
            <div className="stat-pill">
              <Clock size={14} style={{ color: 'var(--warning)' }} />
              <span>Interval</span>
              <span className="stat-pill-value">{profile?.checkinIntervalDays || 7}d</span>
            </div>
            <div className="stat-pill">
              <Shield size={14} style={{ color: '#60a5fa' }} />
              <span>Security Score</span>
              <span className="stat-pill-value">{securityScore}</span>
            </div>
          </motion.div>

          {/* ── Main Bento Grid ── */}
          <div className="bento-grid">

            {/* ── Countdown / Paused Panel ── */}
            {isPaused ? (
              <motion.div variants={itemVariants} className="bento-panel bento-span-8" style={{ alignItems: 'center', justifyContent: 'center', minHeight: 220 }}>
                <Pause size={36} style={{ color: '#94a3b8', marginBottom: 16, opacity: 0.6 }} />
                <h3 style={{ color: 'var(--text-secondary)', marginBottom: 8 }}>Switch is Paused</h3>
                <p className="text-muted text-sm" style={{ maxWidth: 320, textAlign: 'center', lineHeight: 1.7 }}>
                  Your dead man's switch is currently paused. Your check-in timer is not counting down.
                </p>
              </motion.div>
            ) : (
              <motion.div variants={itemVariants} className="bento-panel bento-span-8 countdown-hero">
                <div className="panel-header">
                  <span className="panel-header-label">Next Check-In Deadline</span>
                  <motion.div
                    animate={{
                      boxShadow: status === 'ACTIVE'
                        ? ['0 0 0 0 rgba(16,185,129,0.4)', '0 0 0 10px rgba(16,185,129,0)']
                        : status === 'GRACE_PERIOD'
                        ? ['0 0 0 0 rgba(239,68,68,0.6)', '0 0 0 14px rgba(239,68,68,0)']
                        : 'none'
                    }}
                    transition={{ duration: status === 'GRACE_PERIOD' ? 0.9 : 2.2, repeat: Infinity }}
                    className={`badge ${cfg.cls}`}
                  >
                    <Icon size={12} /> {cfg.label}
                  </motion.div>
                </div>

                <div className="countdown-hero-inner">
                  {/* Timer (last 24h) or Days */}
                  {timeLeft.s ? (
                    <div className={`flex items-center gap-3 ${status === 'GRACE_PERIOD' || status === 'MISSED_CHECKIN' ? 'timer-urgent' : ''}`}>
                      <SlidingDigit value={timeLeft.h} label="HOURS" />
                      <span className="timer-colon">:</span>
                      <SlidingDigit value={timeLeft.m} label="MINS" />
                      <span className="timer-colon">:</span>
                      <SlidingDigit value={timeLeft.s} label="SECS" />
                    </div>
                  ) : (
                    <div className="countdown-days-wrap">
                      <motion.div
                        className={`countdown-days ${isOverdue ? 'text-danger' : daysLeft <= 2 ? 'text-warning' : 'text-success'}`}
                        initial={{ scale: 0.8, opacity: 0 }}
                        animate={{ scale: 1, opacity: 1 }}
                        transition={{ type: 'spring', bounce: 0.4 }}
                      >
                        {isOverdue ? 'OVERDUE' : Math.max(0, daysLeft)}
                      </motion.div>
                      {!isOverdue && <span className="countdown-unit">days remaining</span>}
                    </div>
                  )}

                  {/* Deadline date row */}
                  {deadline && (
                    <div className="countdown-deadline-row">
                      <Calendar size={13} />
                      <span>Deadline: {format(deadline, 'EEEE, MMMM d, yyyy · h:mm a')}</span>
                    </div>
                  )}
                </div>
              </motion.div>
            )}

            {/* ── Check-In Action Panel ── */}
            <motion.div variants={itemVariants} className="bento-panel bento-span-4">
              <div className="checkin-panel-content">
                <div className="checkin-shield-wrap">
                  <motion.div
                    className={`checkin-shield-bg ${status === 'GRACE_PERIOD' || status === 'MISSED_CHECKIN' ? 'danger-ring' : ''}`}
                    animate={status === 'ACTIVE' ? { scale: [1, 1.08, 1] } : status === 'GRACE_PERIOD' ? { scale: [1, 1.12, 1] } : {}}
                    transition={{ duration: 2, repeat: Infinity, ease: 'easeInOut' }}
                  />
                  <Shield
                    size={34}
                    className="checkin-icon"
                    style={{ color: cfg.color }}
                  />
                </div>
                <h3 style={{ fontSize: 16, marginBottom: 4 }}>I'm OK — Check In</h3>
                <p className="text-muted" style={{ fontSize: 12.5, lineHeight: 1.6, marginBottom: 20, maxWidth: 200 }}>
                  Click to confirm you're safe and reset your timer.
                </p>
                <motion.button
                  whileHover={{ scale: 1.03 }}
                  whileTap={{ scale: 0.95 }}
                  className="btn btn-success btn-lg w-full"
                  style={{ marginBottom: 10 }}
                  onClick={() => checkinMutation.mutate()}
                  disabled={checkinMutation.isPending}
                >
                  {checkinMutation.isPending
                    ? <span className="spinner" />
                    : <><CheckCircle size={16} /> Confirm Check-In</>
                  }
                </motion.button>

                {!isPaused ? (
                  <button
                    className="btn btn-ghost btn-sm w-full"
                    style={{ fontSize: 12 }}
                    onClick={() => pauseMutation.mutate()}
                    disabled={pauseMutation.isPending || status !== 'ACTIVE'}
                  >
                    <Pause size={12} /> Pause Switch
                  </button>
                ) : (
                  <button
                    className="btn btn-outline btn-sm w-full"
                    style={{ fontSize: 12 }}
                    onClick={() => resumeMutation.mutate()}
                    disabled={resumeMutation.isPending}
                  >
                    <Play size={12} /> Resume Switch
                  </button>
                )}
              </div>
            </motion.div>

            {/* ── Security Score ── */}
            <motion.div variants={itemVariants} className="bento-panel bento-span-4">
              <div className="panel-header">
                <span className="panel-header-label">Security Score</span>
                <Zap size={14} style={{ color: 'var(--warning)', opacity: 0.8 }} />
              </div>
              <SecurityScoreRing
                score={securityScore}
                checkinCount={profile?.checkinCount || 0}
                streakDays={profile?.streakDays || 0}
                intervalDays={profile?.checkinIntervalDays || 7}
              />
              <div className="panel-divider" />
              <p className="text-muted" style={{ fontSize: 11.5, lineHeight: 1.6 }}>
                {securityScore >= 80
                  ? '🟢 Excellent! Your switch is well-maintained.'
                  : securityScore >= 60
                  ? '🟡 Good standing. Keep checking in consistently.'
                  : '🔴 At risk. Check in now to improve your score.'}
              </p>
            </motion.div>

            {/* ── Activity Feed ── */}
            <motion.div variants={itemVariants} className="bento-panel bento-span-8">
              <div className="panel-header">
                <span className="panel-header-label">Recent Activity</span>
                <span style={{ fontSize: 11, color: 'var(--text-muted)', fontFamily: 'var(--font-mono)' }}>
                  {logs.length} events
                </span>
              </div>
              <div className="activity-list">
                {activityFeed.length > 0 ? activityFeed.map(act => {
                  const iconMap = {
                    checkin:  { icon: CheckCircle, cls: 'icon-checkin' },
                    vault:    { icon: Lock,         cls: 'icon-vault' },
                    settings: { icon: Settings,     cls: 'icon-settings' },
                    default:  { icon: Clock,        cls: '' },
                  };
                  const { icon: ActIcon, cls } = iconMap[act.iconType] || iconMap.default;
                  return (
                    <div key={act.id} className="activity-item">
                      <div className={`activity-icon ${cls}`}><ActIcon size={14} /></div>
                      <div className="activity-content">
                        <div className="activity-title">{act.title}</div>
                        <div className="activity-time">{act.time}</div>
                      </div>
                    </div>
                  );
                }) : (
                  <div className="bento-empty">
                    <Clock size={30} />
                    <span>No recent activity</span>
                  </div>
                )}
              </div>
            </motion.div>

            {/* ── Consistency Heatmap ── */}
            <motion.div variants={itemVariants} className="bento-panel bento-span-6">
              <div className="panel-header">
                <span className="panel-header-label">Consistency (28 days)</span>
                <span style={{ fontSize: 11, color: 'var(--success)', fontFamily: 'var(--font-mono)', fontWeight: 700 }}>
                  {activeCount} / 28 days
                </span>
              </div>
              <div style={{ flex: 1, display: 'flex', flexDirection: 'column', justifyContent: 'flex-end' }}>
                <div className="heatmap-day-labels">
                  {daysOfWeek.map((d, i) => <div key={i}>{d}</div>)}
                </div>
                <div className="heatmap-grid">
                  {heatmapData.map((day, i) => (
                    <div
                      key={i}
                      className="heatmap-cell"
                      data-active={day.active}
                      title={format(day.date, 'MMM d, yyyy')}
                    />
                  ))}
                </div>
                <div className="heatmap-legend">
                  <div className="heatmap-legend-dot" style={{ background: 'rgba(255,255,255,0.06)', border: '1px solid var(--border)' }} />
                  <span>No check-in</span>
                  <div className="heatmap-legend-dot" style={{ background: 'var(--success)', marginLeft: 8 }} />
                  <span>Checked in</span>
                </div>
              </div>
            </motion.div>

            {/* ── Quick Links / Info Panel ── */}
            <motion.div variants={itemVariants} className="bento-panel bento-span-6">
              <div className="panel-header">
                <span className="panel-header-label">Quick Actions</span>
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 10, flex: 1 }}>
                <div className="info-panel">
                  <div className="info-panel-icon"><Lock size={18} /></div>
                  <div className="info-panel-text">
                    <div className="info-panel-title">Vault Items</div>
                    <div className="info-panel-desc">
                      Securely store files, notes &amp; credentials that release to your recipients.
                    </div>
                  </div>
                </div>
                <div className="info-panel" style={{ background: 'linear-gradient(135deg, rgba(16,185,129,0.08), rgba(79,70,229,0.05))', borderColor: 'rgba(16,185,129,0.18)' }}>
                  <div className="info-panel-icon" style={{ background: 'rgba(16,185,129,0.12)', border: '1px solid rgba(16,185,129,0.2)', color: '#34d399' }}>
                    <Users size={18} />
                  </div>
                  <div className="info-panel-text">
                    <div className="info-panel-title">Recipients</div>
                    <div className="info-panel-desc">
                      Manage trusted people who receive your vault when the switch triggers.
                    </div>
                  </div>
                </div>
                <div className="info-panel" style={{ background: 'linear-gradient(135deg, rgba(245,158,11,0.06), rgba(239,68,68,0.04))', borderColor: 'rgba(245,158,11,0.15)' }}>
                  <div className="info-panel-icon" style={{ background: 'rgba(245,158,11,0.1)', border: '1px solid rgba(245,158,11,0.2)', color: '#fbbf24' }}>
                    <Info size={18} />
                  </div>
                  <div className="info-panel-text">
                    <div className="info-panel-title">Grace Period: {profile?.gracePeriodDays || 3} days</div>
                    <div className="info-panel-desc">
                      Extra time given after a missed check-in before your vault releases.
                    </div>
                  </div>
                </div>
              </div>
            </motion.div>

          </div>
        </>
      )}
    </motion.div>
  );
}

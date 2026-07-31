import { useState, useEffect } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { checkinApi, authApi } from '../api/client';
import { useAuthStore } from '../store/authStore';
import { Shield, Clock, CheckCircle, AlertTriangle, XCircle, Pause, Play, TrendingUp, Calendar } from 'lucide-react';
import { formatDistanceToNow, format } from 'date-fns';
import toast from 'react-hot-toast';
import { motion, AnimatePresence } from 'framer-motion';
import './DashboardPage.css';

const statusConfig = {
  ACTIVE:         { label: 'Active',         icon: CheckCircle,   cls: 'badge-active',   color: '#10b981', msg: 'Your switch is active and monitoring. Keep checking in regularly!' },
  MISSED_CHECKIN: { label: 'Missed Check-In', icon: AlertTriangle,  cls: 'badge-warning',  color: '#f59e0b', msg: 'You missed your check-in deadline. Your grace period has started.' },
  GRACE_PERIOD:   { label: 'Grace Period',    icon: AlertTriangle,  cls: 'badge-danger',   color: '#ef4444', msg: '🚨 Grace period active — check in NOW to prevent vault release!' },
  RELEASED:       { label: 'Released',        icon: XCircle,       cls: 'badge-released', color: '#a78bfa', msg: 'Your vault contents have been released to your designated recipients.' },
  PAUSED:         { label: 'Paused',          icon: Pause,         cls: 'badge-paused',   color: '#94a3b8', msg: 'Your switch is paused. Resume when you\'re ready.' },
};

function SlidingDigit({ value, label }) {
  return (
    <div className="digit-group">
      <div className="digit-box">
        <AnimatePresence mode="popLayout">
          <motion.span
            key={value}
            initial={{ y: 20, opacity: 0, filter: 'blur(2px)' }}
            animate={{ y: 0, opacity: 1, filter: 'blur(0px)' }}
            exit={{ y: -20, opacity: 0, filter: 'blur(2px)' }}
            transition={{ type: 'spring', stiffness: 300, damping: 25 }}
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
  const daysLeft = profile?.daysUntilDeadline;
  const isOverdue = profile?.isOverdue;
  const isReleased = status === 'RELEASED';
  const isPaused = status === 'PAUSED';

  // Timer logic
  const [timeLeft, setTimeLeft] = useState({ h: '', m: '', s: '' });
  
  useEffect(() => {
    if (!deadlineString || isOverdue || isReleased || isPaused) return;
    
    const deadlineDate = new Date(deadlineString);
    const updateTimer = () => {
      const now = new Date();
      const diff = deadlineDate - now;
      if (diff > 0 && diff <= 24 * 60 * 60 * 1000) {
        const h = Math.floor(diff / (1000 * 60 * 60)).toString().padStart(2, '0');
        const m = Math.floor((diff / 1000 / 60) % 60).toString().padStart(2, '0');
        const s = Math.floor((diff / 1000) % 60).toString().padStart(2, '0');
        setTimeLeft({ h, m, s });
      } else {
        setTimeLeft({ h: '', m: '', s: '' });
      }
    };
    
    updateTimer();
    const interval = setInterval(updateTimer, 1000);
    return () => clearInterval(interval);
  }, [deadlineString, isOverdue, isReleased, isPaused]);

  if (isLoading) return (
    <div className="page-container flex items-center justify-center" style={{ minHeight: '60vh' }}>
      <div className="w-full max-w-lg space-y-4">
        <div className="skeleton-shimmer h-32 w-full mb-4"></div>
        <div className="skeleton-shimmer h-40 w-full mb-4"></div>
        <div className="skeleton-shimmer h-24 w-full"></div>
      </div>
    </div>
  );

  const containerVariants = {
    hidden: { opacity: 0 },
    show: { opacity: 1, transition: { staggerChildren: 0.1 } }
  };
  const itemVariants = {
    hidden: { opacity: 0, y: 20 },
    show: { opacity: 1, y: 0, transition: { type: 'spring', damping: 20, stiffness: 100 } }
  };

  // Generate Empty Heatmap Data for now (until connected to backend)
  const heatmapData = Array.from({ length: 28 }).map((_, i) => ({
    date: new Date(Date.now() - (27 - i) * 24 * 60 * 60 * 1000),
    active: false
  }));
  const daysOfWeek = ['M', 'T', 'W', 'T', 'F', 'S', 'S'];

  // Empty Activity Feed (no fake data)
  const activityFeed = [];

  return (
    <motion.div className="dashboard-content" variants={containerVariants} initial="hidden" animate="show">
      <div className={`dashboard-ambient-bg ambient-${status}`} />
      
      {isReleased ? (
        <motion.div 
          className="bento-panel released-card text-center"
          initial={{ scale: 0.9, opacity: 0 }}
          animate={{ scale: 1, opacity: 1 }}
          transition={{ type: 'spring', bounce: 0.5 }}
        >
          <div className="released-icon mb-4">📬</div>
          <h1 className="text-danger">Vault Released</h1>
          <p className="text-muted mt-2">
            Your vault contents were permanently released to your designated recipients on{' '}
            <strong className="text-primary">
              {profile?.releasedAt ? format(new Date(profile.releasedAt), 'MMM d, yyyy') : '—'}
            </strong>.
          </p>
        </motion.div>
      ) : (
        <div className="flex-col w-full">
          {/* Top Quick Stats Bar */}
          <motion.div variants={itemVariants} className="stats-bar">
            <div className="stat-pill">
              <TrendingUp size={16} className="text-success" />
              <span>Streak</span>
              <span className="stat-pill-value">{profile?.streakDays || 0} days</span>
            </div>
            <div className="stat-pill">
              <CheckCircle size={16} className="text-primary-dark" />
              <span>Check-ins</span>
              <span className="stat-pill-value">{profile?.checkinCount || 0}</span>
            </div>
          </motion.div>

          <div className="bento-grid">
            
            {/* Center-Left: Hero Countdown Timer */}
            {!isPaused && (
              <motion.div variants={itemVariants} className="bento-panel bento-span-8 bento-row-2">
                <div className="panel-header">
                  <span>Next Check-In Deadline</span>
                  <motion.div 
                    animate={{ 
                      boxShadow: status === 'ACTIVE' ? ['0 0 0 0 rgba(16,185,129,0.4)', '0 0 0 8px rgba(16,185,129,0)'] :
                                 status === 'GRACE_PERIOD' ? ['0 0 0 0 rgba(239,68,68,0.6)', '0 0 0 16px rgba(239,68,68,0)'] : 'none'
                    }}
                    transition={{ duration: status === 'GRACE_PERIOD' ? 1 : 2.5, repeat: Infinity }}
                    className={`badge ${cfg.cls}`}
                  >
                    <Icon size={14} /> {cfg.label}
                  </motion.div>
                </div>
                
                <div className="flex-1 flex flex-col justify-center items-center py-6">
                  {timeLeft.s ? (
                    <div className="flex justify-center gap-6 timer-urgent">
                      <SlidingDigit value={timeLeft.h} label="HOURS" />
                      <span className="text-4xl font-bold opacity-30 mt-4">:</span>
                      <SlidingDigit value={timeLeft.m} label="MINS" />
                      <span className="text-4xl font-bold opacity-30 mt-4">:</span>
                      <SlidingDigit value={timeLeft.s} label="SECS" />
                    </div>
                  ) : (
                    <div className={`countdown-days ${isOverdue ? 'text-danger' : daysLeft <= 3 ? 'text-warning' : 'text-primary'}`}>
                      {isOverdue ? 'OVERDUE' : `${Math.max(0, daysLeft ?? 0)}`}
                      {!isOverdue && <span className="countdown-unit text-muted text-sm ml-2">DAYS</span>}
                    </div>
                  )}
                  
                  {deadline && (
                    <div className="text-center mt-8 text-sm text-muted flex items-center justify-center gap-2">
                      <Calendar size={14} />
                      {format(deadline, 'EEEE, MMMM d, yyyy')}
                    </div>
                  )}
                </div>
              </motion.div>
            )}

            {/* Center-Right: Action Panel */}
            <motion.div variants={itemVariants} className="bento-panel bento-span-4 text-center justify-center">
              <Shield size={42} className="checkin-icon mx-auto mb-4 opacity-80" style={{ color: cfg.color }} />
              <h3 className="mb-2">I'm OK — Check In</h3>
              <p className="text-muted text-xs mb-6">
                Click to reset your timer and confirm you're safe.
              </p>
              <motion.button
                whileHover={{ scale: 1.02 }}
                whileTap={{ scale: 0.95 }}
                className="btn btn-success btn-lg w-full mb-4"
                onClick={() => checkinMutation.mutate()}
                disabled={checkinMutation.isPending}
              >
                {checkinMutation.isPending ? <span className="spinner" /> : (
                  <><CheckCircle size={18} /> Confirm Check-In</>
                )}
              </motion.button>
              
              {!isPaused ? (
                <button className="btn btn-ghost btn-sm text-xs w-full" onClick={() => pauseMutation.mutate()} disabled={pauseMutation.isPending || status !== 'ACTIVE'}>
                  <Pause size={12} /> Pause Switch
                </button>
              ) : (
                <button className="btn btn-outline btn-sm text-xs w-full" onClick={() => resumeMutation.mutate()} disabled={resumeMutation.isPending}>
                  <Play size={12} /> Resume Switch
                </button>
              )}
            </motion.div>

            {/* Bottom Row: Activity Feed */}
            <motion.div variants={itemVariants} className="bento-panel bento-span-6">
              <div className="panel-header">
                <span>Recent Activity</span>
              </div>
              <div className="activity-list">
                {activityFeed.length > 0 ? (
                  activityFeed.map(act => (
                    <div key={act.id} className="activity-item">
                      <div className="activity-icon"><act.icon size={14} /></div>
                      <div className="activity-content">
                        <div className="activity-title">{act.title}</div>
                        <div className="activity-time">{act.time}</div>
                      </div>
                    </div>
                  ))
                ) : (
                  <div className="bento-empty">
                    <Clock size={32} />
                    <span>No recent activity</span>
                  </div>
                )}
              </div>
            </motion.div>

            {/* Bottom Row: Heatmap */}
            <motion.div variants={itemVariants} className="bento-panel bento-span-6">
              <div className="panel-header">
                <span>Consistency Pattern</span>
              </div>
              <div className="flex-1 flex flex-col justify-end">
                <div className="heatmap-day-labels">
                  {daysOfWeek.map((d, i) => <div key={i}>{d}</div>)}
                </div>
                <div className="heatmap-grid">
                  {heatmapData.map((day, i) => (
                    <div 
                      key={i} 
                      className="heatmap-cell" 
                      data-active={day.active}
                      title={format(day.date, 'MMM d')}
                    />
                  ))}
                </div>
              </div>
            </motion.div>

          </div>
        </div>
      )}
    </motion.div>
  );
}

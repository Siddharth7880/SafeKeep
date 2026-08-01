import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuthStore } from '../store/authStore';
import {
  Shield, LayoutDashboard, Lock, Users, Settings, ScrollText, LogOut, Menu, X, Bell
} from 'lucide-react';
import { useState } from 'react';
import ProfileModal from './ProfileModal';
import { API_BASE } from '../api/client';
import './Layout.css';

const navItems = [
  { to: '/dashboard',  icon: LayoutDashboard, label: 'Dashboard' },
  { to: '/vault',      icon: Lock,             label: 'Vault' },
  { to: '/recipients', icon: Users,            label: 'Recipients' },
  { to: '/settings',   icon: Settings,         label: 'Settings' },
  { to: '/audit',      icon: ScrollText,       label: 'Audit Log' },
];

export default function Layout({ children }) {
  const location = useLocation();
  const navigate = useNavigate();
  const { user, logout } = useAuthStore();
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [profileModalOpen, setProfileModalOpen] = useState(false);

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const getStatusClass = (status) => {
    const map = {
      ACTIVE: 'active', MISSED_CHECKIN: 'warning', GRACE_PERIOD: 'danger',
      RELEASED: 'released', PAUSED: 'paused'
    };
    return map[status] || 'active';
  };

  const currentPage = navItems.find(n => location.pathname.startsWith(n.to))?.label || 'SafeKeep';

  return (
    <div className="layout">
      {/* Mobile Overlay */}
      {sidebarOpen && <div className="sidebar-overlay" onClick={() => setSidebarOpen(false)} />}

      {/* Sidebar */}
      <aside className={`sidebar ${sidebarOpen ? 'sidebar-open' : ''}`}>
        <div className="sidebar-header">
          <div className="sidebar-logo">
            <Shield size={22} className="logo-icon animate-float" />
            <span>SafeKeep</span>
          </div>
          <button className="sidebar-close" onClick={() => setSidebarOpen(false)}>
            <X size={18} />
          </button>
        </div>

        {/* User status */}
        {user && (
          <div className="sidebar-user" onClick={() => setProfileModalOpen(true)} style={{ cursor: 'pointer' }}>
            <div className="sidebar-user-avatar">
              {user.profilePhotoUrl ? (
                <img src={`${API_BASE}${user.profilePhotoUrl}`} alt="Profile" style={{ width: '100%', height: '100%', borderRadius: '50%', objectFit: 'cover' }} />
              ) : (
                user.fullName?.charAt(0).toUpperCase() || 'U'
              )}
            </div>
            <div className="sidebar-user-info">
              <div className="sidebar-user-name">{user.fullName}</div>
              <span className={`badge badge-${getStatusClass(user.status)}`}>
                ● {user.status || 'ACTIVE'}
              </span>
            </div>
          </div>
        )}

        {/* Nav */}
        <nav className="sidebar-nav">
          {navItems.map(({ to, icon: Icon, label }) => (
            <Link
              key={to}
              to={to}
              className={`nav-item ${location.pathname === to ? 'nav-item-active' : ''}`}
              onClick={() => setSidebarOpen(false)}
            >
              <Icon size={17} />
              <span>{label}</span>
            </Link>
          ))}
        </nav>

        <div className="sidebar-bottom">
          <button className="sidebar-logout" onClick={handleLogout}>
            <LogOut size={16} />
            <span>Sign Out</span>
          </button>
        </div>
      </aside>

      {/* Main Content */}
      <div className="layout-main">
        <header className="layout-topbar">
          <button className="topbar-menu-btn" onClick={() => setSidebarOpen(true)}>
            <Menu size={20} />
          </button>
          <div className="topbar-breadcrumb">{currentPage}</div>
          <div className="topbar-right">
            <span style={{ fontSize: 12, color: 'var(--text-muted)', padding: '4px 10px', background: 'rgba(255,255,255,0.04)', borderRadius: 100, border: '1px solid var(--border)' }}>
              🔒 Encrypted
            </span>
          </div>
        </header>
        <main className="layout-content">
          {children}
        </main>
      </div>
      
      <ProfileModal isOpen={profileModalOpen} onClose={() => setProfileModalOpen(false)} />
    </div>
  );
}

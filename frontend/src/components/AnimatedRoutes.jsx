import { Routes, Route, Navigate, useLocation } from 'react-router-dom';
import { AnimatePresence } from 'framer-motion';
import ProtectedRoute from './ProtectedRoute';
import LandingPage from '../pages/LandingPage';
import LoginPage from '../pages/LoginPage';
import RegisterPage from '../pages/RegisterPage';
import DashboardPage from '../pages/DashboardPage';
import VaultPage from '../pages/VaultPage';
import RecipientsPage from '../pages/RecipientsPage';
import SettingsPage from '../pages/SettingsPage';
import AuditLogPage from '../pages/AuditLogPage';
import VerifyEmailPage from '../pages/VerifyEmailPage';
import Layout from './Layout';
import PageTransition from './PageTransition';

export default function AnimatedRoutes() {
  const location = useLocation();

  return (
    <AnimatePresence mode="wait">
      <Routes location={location} key={location.pathname}>
        <Route path="/" element={<PageTransition><LandingPage /></PageTransition>} />
        <Route path="/login" element={<PageTransition><LoginPage /></PageTransition>} />
        <Route path="/register" element={<PageTransition><RegisterPage /></PageTransition>} />
        <Route path="/verify-email" element={<PageTransition><VerifyEmailPage /></PageTransition>} />
        
        <Route path="/dashboard" element={<ProtectedRoute><Layout><PageTransition><DashboardPage /></PageTransition></Layout></ProtectedRoute>} />
        <Route path="/vault" element={<ProtectedRoute><Layout><PageTransition><VaultPage /></PageTransition></Layout></ProtectedRoute>} />
        <Route path="/recipients" element={<ProtectedRoute><Layout><PageTransition><RecipientsPage /></PageTransition></Layout></ProtectedRoute>} />
        <Route path="/settings" element={<ProtectedRoute><Layout><PageTransition><SettingsPage /></PageTransition></Layout></ProtectedRoute>} />
        <Route path="/audit" element={<ProtectedRoute><Layout><PageTransition><AuditLogPage /></PageTransition></Layout></ProtectedRoute>} />
        
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </AnimatePresence>
  );
}

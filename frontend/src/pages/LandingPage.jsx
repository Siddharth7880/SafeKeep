import { Link } from 'react-router-dom';
import { Shield, Lock, Users, Clock, ArrowRight, CheckCircle, Quote } from 'lucide-react';
import './LandingPage.css';

const features = [
  { icon: Lock,   title: 'Zero-Knowledge Encryption', desc: 'AES-256-GCM with envelope key management. Even we cannot read your content.' },
  { icon: Clock,  title: 'Reliable Dead Man\'s Switch', desc: 'Quartz Scheduler tracks your check-in timer with guaranteed delivery — no missed triggers.' },
  { icon: Users,  title: 'Trusted Recipients', desc: 'Designate exactly who receives what. Signed, expiring access links. No email attachment risks.' },
  { icon: Shield, title: 'Immutable Audit Trail', desc: 'Every state transition, check-in, and release permanently logged for verifiability.' },
];

const steps = [
  { n: '01', title: 'Create Your Vault', desc: 'Securely store passwords, messages, documents, and final instructions.' },
  { n: '02', title: 'Add Recipients', desc: 'Designate trusted contacts and assign which vault items they receive.' },
  { n: '03', title: 'Set Your Schedule', desc: 'Choose how often you need to check in. Anywhere from daily to yearly.' },
  { n: '04', title: 'Check In Regularly', desc: 'Click one button to reset your timer. If you don\'t, the system acts for you.' },
];

const quotes = [
  { text: "Privacy is not an option, and it shouldn't be the price we accept for just getting on the Internet.", author: "Gary Kovacs" },
  { text: "The legacy you leave is the life you lead. Protect your digital footprint with absolute certainty.", author: "Security Architect" },
];

export default function LandingPage() {
  return (
    <div className="landing">
      {/* Nav */}
      <nav className="landing-nav">
        <div className="landing-nav-inner">
          <div className="landing-logo">
            <Shield size={24} className="logo-icon animate-pulse" />
            <span>SafeKeep</span>
          </div>
          <div className="landing-nav-links">
            <a href="#features">Features</a>
            <a href="#how-it-works">How It Works</a>
            <Link to="/login" className="btn btn-ghost btn-sm">Sign In</Link>
            <Link to="/register" className="btn btn-primary btn-sm">Get Started</Link>
          </div>
        </div>
      </nav>

      {/* Hero (Asymmetrical) */}
      <section className="hero">
        <div className="hero-bg-glow" />
        <div className="hero-grid">
          <div className="hero-content stagger-children">
            <div className="hero-badge animate-fade-in">
              <span className="badge badge-active">● Live System</span>
            </div>
            <h1 className="hero-title animate-slide-up">
              Your Digital Legacy,<br />
              <span className="gradient-text">Protected & Prepared</span>
            </h1>
            <p className="hero-subtitle animate-slide-up">
              SafeKeep automatically releases your encrypted passwords, messages, and documents
              to trusted recipients if you stop checking in — ensuring your digital life reaches
              the right people, no matter what happens.
            </p>
            <div className="hero-actions animate-slide-up">
              <Link to="/register" className="btn btn-primary btn-lg">
                Start Protecting Your Legacy
                <ArrowRight size={18} />
              </Link>
              <Link to="/login" className="btn btn-ghost btn-lg">Sign In</Link>
            </div>
            <div className="hero-trust animate-fade-in">
              {['AES-256 Encrypted', 'Zero-Knowledge', 'Automated Release', 'Audit Trail'].map(t => (
                <div key={t} className="trust-item">
                  <CheckCircle size={13} />
                  <span>{t}</span>
                </div>
              ))}
            </div>
          </div>
          
          <div className="hero-visual animate-slide-right">
            <div className="glass-card mockup-card float-slow">
              <div className="mockup-header">
                <Shield size={20} className="text-success" />
                <span className="text-sm font-medium">Vault Status: Active</span>
              </div>
              <div className="mockup-body">
                <div className="mockup-line" style={{ width: '80%' }} />
                <div className="mockup-line" style={{ width: '60%' }} />
                <div className="mockup-line" style={{ width: '90%' }} />
                <div className="mockup-btn" />
              </div>
            </div>
            <div className="glass-card mockup-card mockup-small float-fast delay-1">
              <Clock size={16} className="text-primary-light" />
              <div style={{ marginLeft: 12 }}>
                <div className="text-xs text-muted">Next Check-In</div>
                <div className="text-sm font-bold">14 Days</div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* Quotes Section */}
      <section className="section quotes-section">
        <div className="quotes-grid">
          {quotes.map((q, i) => (
            <div key={i} className="quote-card glass stagger-children">
              <Quote size={28} className="quote-icon text-primary-light" />
              <p className="quote-text">{q.text}</p>
              <div className="quote-author">— {q.author}</div>
            </div>
          ))}
        </div>
      </section>

      {/* Features */}
      <section id="features" className="section">
        <div className="section-header">
          <div className="section-divider">Why SafeKeep</div>
          <h2>Built for <span className="gradient-text">Security & Reliability</span></h2>
          <p>Production-grade architecture — the same patterns used in critical infrastructure monitoring.</p>
        </div>
        <div className="features-grid stagger-children">
          {features.map(({ icon: Icon, title, desc }) => (
            <div key={title} className="card feature-card glass">
              <div className="feature-icon"><Icon size={22} /></div>
              <h3>{title}</h3>
              <p className="text-muted text-sm" style={{ marginTop: 8 }}>{desc}</p>
            </div>
          ))}
        </div>
      </section>

      {/* How It Works */}
      <section id="how-it-works" className="section steps-section" style={{ maxWidth: '100%' }}>
        <div style={{ maxWidth: 1200, margin: '0 auto', padding: '100px 32px' }}>
          <div className="section-header">
            <div className="section-divider">Simple Process</div>
            <h2>How It <span className="gradient-text">Works</span></h2>
            <p>Simple to set up. Automatic when it matters most.</p>
          </div>
          <div className="steps-grid">
            {steps.map(({ n, title, desc }) => (
              <div key={n} className="step-card glass">
                <div className="step-number">{n}</div>
                <h3>{title}</h3>
                <p className="text-muted text-sm" style={{ marginTop: 8 }}>{desc}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* CTA */}
      <section className="cta-section">
        <div className="cta-glow" />
        <div style={{ position: 'relative', zIndex: 1 }}>
          <h2>Start Protecting Your Digital Legacy Today</h2>
          <p className="text-muted" style={{ marginTop: 12 }}>Free to use. Your content stays encrypted and private.</p>
          <Link to="/register" className="btn btn-primary btn-lg hover-lift" style={{ marginTop: 32, display: 'inline-flex' }}>
            Create Your Vault
            <ArrowRight size={18} />
          </Link>
        </div>
      </section>

      {/* Footer */}
      <footer className="landing-footer">
        <div className="flex items-center gap-2" style={{ justifyContent: 'center' }}>
          <Shield size={16} className="logo-icon" />
          <span style={{ fontWeight: 700 }}>SafeKeep</span>
        </div>
        <p className="text-muted text-sm" style={{ marginTop: 8 }}>Digital Legacy Protection Platform</p>
      </footer>
    </div>
  );
}

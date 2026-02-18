import React from 'react';
import { Link, useLocation } from 'react-router-dom';
import { Triangle, LogOut } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import './Navbar.css';

const Navbar: React.FC = () => {
  const { user, logout } = useAuth();
  const location = useLocation();

  if (!user) return null;

  const isActive = (path: string): string =>
    location.pathname === path ? 'nav-link active' : 'nav-link';

  return (
    <nav className="navbar">
      <div className="navbar-left">
        <Link to="/" className="navbar-brand">
          <Triangle size={22} fill="currentColor" />
          <span>Vercel Clone</span>
        </Link>
        <div className="navbar-links">
          <Link to="/" className={isActive('/')}>Overview</Link>
          <Link to="/new" className={isActive('/new')}>New Project</Link>
        </div>
      </div>
      <div className="navbar-right">
        <span className="navbar-email">{user.email}</span>
        <div className="navbar-avatar">
          {user.username?.charAt(0).toUpperCase() || 'U'}
        </div>
        <button className="btn-icon" onClick={logout} title="Logout">
          <LogOut size={18} />
        </button>
      </div>
    </nav>
  );
};

export default Navbar;

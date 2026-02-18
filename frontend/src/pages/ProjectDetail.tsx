import React, { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import {
  ArrowLeft, Rocket, Settings, Globe, GitBranch, Clock,
  ExternalLink, Trash2, RefreshCw
} from 'lucide-react';
import Layout from '../components/Layout';
import StatusBadge from '../components/StatusBadge';
import { projectAPI, deployAPI } from '../services/api';
import { Project, Deployment } from '../types';
import './ProjectDetail.css';

type Tab = 'deployments' | 'settings';

const ProjectDetail: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();

  const [project, setProject] = useState<Project | null>(null);
  const [deployments, setDeployments] = useState<Deployment[]>([]);
  const [activeTab, setActiveTab] = useState<Tab>('deployments');
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string>('');
  const [deleting, setDeleting] = useState<boolean>(false);

  // Settings form state
  const [editName, setEditName] = useState<string>('');
  const [editDesc, setEditDesc] = useState<string>('');
  const [editDomain, setEditDomain] = useState<string>('');
  const [editFramework, setEditFramework] = useState<string>('');
  const [saving, setSaving] = useState<boolean>(false);

  const fetchProject = useCallback(async (): Promise<void> => {
    if (!id) return;
    try {
      const res = await projectAPI.get(id);
      setProject(res.data);
      setEditName(res.data.name);
      setEditDesc(res.data.description || '');
      setEditDomain(res.data.customDomain || '');
      setEditFramework(res.data.framework || '');
    } catch (err: any) {
      setError('Failed to load project.');
    } finally {
      setLoading(false);
    }
  }, [id]);

  const fetchDeployments = useCallback(async (): Promise<void> => {
    if (!id) return;
    try {
      const res = await deployAPI.listByProject(id);
      setDeployments(res.data);
    } catch {
      // Deployments may not be available yet
    }
  }, [id]);

  useEffect(() => {
    fetchProject();
    fetchDeployments();
  }, [fetchProject, fetchDeployments]);

  const handleDeploy = (): void => {
    navigate(`/project/${id}/deploy`);
  };

  const handleSaveSettings = async (): Promise<void> => {
    if (!id) return;
    setSaving(true);
    try {
      const res = await projectAPI.update(id, {
        name: editName,
        description: editDesc,
        customDomain: editDomain || undefined,
        framework: editFramework,
      });
      setProject(res.data);
      setError('');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to update.');
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (): Promise<void> => {
    if (!id) return;
    const confirmed = window.confirm(
      'Are you sure you want to delete this project? This action cannot be undone.'
    );
    if (!confirmed) return;

    setDeleting(true);
    try {
      await projectAPI.delete(id);
      navigate('/');
    } catch (err: any) {
      setError('Failed to delete project.');
      setDeleting(false);
    }
  };

  const formatDate = (dateStr: string): string => {
    return new Date(dateStr).toLocaleString();
  };

  const formatDuration = (seconds: number | null): string => {
    if (!seconds) return '-';
    if (seconds < 60) return `${seconds}s`;
    return `${Math.floor(seconds / 60)}m ${seconds % 60}s`;
  };

  if (loading) {
    return (
      <Layout>
        <div className="loading-page"><div className="spinner" /></div>
      </Layout>
    );
  }

  if (!project) {
    return (
      <Layout>
        <div className="empty-state">
          <h3>Project not found</h3>
          <Link to="/" className="btn btn-primary">Go to Dashboard</Link>
        </div>
      </Layout>
    );
  }

  return (
    <Layout>
      <button className="btn-back" onClick={() => navigate('/')}>
        <ArrowLeft size={16} />
        Back to Projects
      </button>

      {/* Project Header */}
      <div className="project-header">
        <div className="project-header__left">
          <div className="project-header__icon">
            {project.name.charAt(0).toUpperCase()}
          </div>
          <div>
            <h1 className="project-header__name">{project.name}</h1>
            <p className="project-header__meta">
              <GitBranch size={14} />
              {project.framework}
              {project.repositoryUrl && (
                <>
                  <span className="dot">·</span>
                  <a
                    href={project.repositoryUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="project-header__repo"
                  >
                    {project.repositoryUrl.replace('https://github.com/', '')}
                    <ExternalLink size={12} />
                  </a>
                </>
              )}
            </p>
          </div>
        </div>
        <button className="btn btn-primary" onClick={handleDeploy}>
          <Rocket size={16} />
          Deploy
        </button>
      </div>

      {error && <div className="alert alert--error">{error}</div>}

      {/* Tabs */}
      <div className="tabs">
        <button
          className={`tab ${activeTab === 'deployments' ? 'active' : ''}`}
          onClick={() => setActiveTab('deployments')}
        >
          <Rocket size={14} />
          Deployments
        </button>
        <button
          className={`tab ${activeTab === 'settings' ? 'active' : ''}`}
          onClick={() => setActiveTab('settings')}
        >
          <Settings size={14} />
          Settings
        </button>
      </div>

      {/* Deployments Tab */}
      {activeTab === 'deployments' && (
        <div className="deployments-section">
          <div className="section-header">
            <h2>Deployment History</h2>
            <button className="btn btn-sm" onClick={fetchDeployments}>
              <RefreshCw size={14} />
              Refresh
            </button>
          </div>

          {deployments.length === 0 ? (
            <div className="empty-state">
              <Rocket size={40} strokeWidth={1} />
              <h3>No deployments yet</h3>
              <p>Deploy your project to see it live.</p>
              <button className="btn btn-primary" onClick={handleDeploy}>
                <Rocket size={16} />
                Deploy Now
              </button>
            </div>
          ) : (
            <div className="deployment-list">
              {deployments.map((dep) => (
                <div key={dep.deploymentId} className="deployment-item">
                  <div className="deployment-item__main">
                    <span className="deployment-item__id">{dep.deploymentId}</span>
                    <StatusBadge status={dep.status} />
                  </div>
                  <div className="deployment-item__details">
                    {dep.deploymentUrl && (
                      <a
                        href={dep.deploymentUrl}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="deployment-item__url"
                      >
                        <Globe size={13} />
                        {dep.deploymentUrl}
                        <ExternalLink size={11} />
                      </a>
                    )}
                    <span className="deployment-item__duration">
                      {formatDuration(dep.buildDurationSeconds)}
                    </span>
                    <span className="deployment-item__time">
                      <Clock size={13} />
                      {formatDate(dep.createdAt)}
                    </span>
                  </div>
                  {dep.errorMessage && (
                    <div className="deployment-item__error">
                      {dep.errorMessage}
                    </div>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* Settings Tab */}
      {activeTab === 'settings' && (
        <div className="settings-section">
          <div className="settings-card">
            <h3>General Settings</h3>
            <div className="form-group">
              <label className="form-label">Project Name</label>
              <input
                type="text"
                className="form-input"
                value={editName}
                onChange={(e) => setEditName(e.target.value)}
              />
            </div>
            <div className="form-group">
              <label className="form-label">Description</label>
              <input
                type="text"
                className="form-input"
                value={editDesc}
                onChange={(e) => setEditDesc(e.target.value)}
              />
            </div>
            <div className="form-group">
              <label className="form-label">Framework</label>
              <select
                className="form-select"
                value={editFramework}
                onChange={(e) => setEditFramework(e.target.value)}
              >
                <option value="react">React</option>
                <option value="next">Next.js</option>
                <option value="vue">Vue.js</option>
                <option value="angular">Angular</option>
                <option value="svelte">Svelte</option>
                <option value="static">Static / HTML</option>
              </select>
            </div>
            <div className="form-group">
              <label className="form-label">Custom Domain</label>
              <input
                type="text"
                className="form-input"
                placeholder="myapp.example.com"
                value={editDomain}
                onChange={(e) => setEditDomain(e.target.value)}
              />
              <span className="form-helper">
                Configure a custom domain for this project.
              </span>
            </div>
            <button
              className="btn btn-primary"
              onClick={handleSaveSettings}
              disabled={saving}
            >
              {saving ? <span className="spinner" /> : 'Save Changes'}
            </button>
          </div>

          <div className="settings-card settings-card--danger">
            <h3>Danger Zone</h3>
            <p className="settings-danger-text">
              Deleting this project will permanently remove all deployments and data.
            </p>
            <button
              className="btn btn-danger"
              onClick={handleDelete}
              disabled={deleting}
            >
              <Trash2 size={16} />
              {deleting ? 'Deleting...' : 'Delete Project'}
            </button>
          </div>
        </div>
      )}
    </Layout>
  );
};

export default ProjectDetail;

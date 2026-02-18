import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { Plus, GitBranch, Globe, Clock, FolderOpen } from 'lucide-react';
import Layout from '../components/Layout';
import StatusBadge from '../components/StatusBadge';
import { projectAPI } from '../services/api';
import { Project } from '../types';
import './Dashboard.css';

const Dashboard: React.FC = () => {
  const [projects, setProjects] = useState<Project[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string>('');

  useEffect(() => {
    fetchProjects();
  }, []);

  const fetchProjects = async (): Promise<void> => {
    try {
      const res = await projectAPI.list();
      setProjects(res.data);
    } catch (err: any) {
      setError('Failed to load projects.');
    } finally {
      setLoading(false);
    }
  };

  const formatDate = (dateStr: string): string => {
    const date = new Date(dateStr);
    const now = new Date();
    const diff = now.getTime() - date.getTime();
    const days = Math.floor(diff / (1000 * 60 * 60 * 24));
    const hours = Math.floor(diff / (1000 * 60 * 60));
    const minutes = Math.floor(diff / (1000 * 60));

    if (minutes < 1) return 'just now';
    if (minutes < 60) return `${minutes}m ago`;
    if (hours < 24) return `${hours}h ago`;
    if (days < 30) return `${days}d ago`;
    return date.toLocaleDateString();
  };

  const getFrameworkLabel = (fw: string): string => {
    const map: Record<string, string> = {
      react: 'React',
      next: 'Next.js',
      vue: 'Vue',
      angular: 'Angular',
      svelte: 'Svelte',
      static: 'Static',
    };
    return map[fw] || fw;
  };

  if (loading) {
    return (
      <Layout>
        <div className="loading-page"><div className="spinner" /></div>
      </Layout>
    );
  }

  return (
    <Layout>
      <div className="page-header">
        <div>
          <h1 className="page-title">Projects</h1>
          <p className="page-subtitle">Manage and deploy your applications</p>
        </div>
        <Link to="/new" className="btn btn-primary">
          <Plus size={16} />
          New Project
        </Link>
      </div>

      {error && <div className="alert alert--error">{error}</div>}

      {projects.length === 0 ? (
        <div className="empty-state">
          <FolderOpen size={48} strokeWidth={1} />
          <h3>No projects yet</h3>
          <p>Create your first project to get started with deployments.</p>
          <Link to="/new" className="btn btn-primary">
            <Plus size={16} />
            Create Project
          </Link>
        </div>
      ) : (
        <div className="project-grid">
          {projects.map((project) => {
            const latestDeployment = project.deployments?.[0];
            return (
              <Link
                to={`/project/${project.id}`}
                key={project.id}
                className="card card-clickable project-card"
              >
                <div className="project-card__header">
                  <div className="project-card__icon">
                    {project.name.charAt(0).toUpperCase()}
                  </div>
                  <div className="project-card__info">
                    <h3 className="project-card__name">{project.name}</h3>
                    {project.description && (
                      <p className="project-card__desc">{project.description}</p>
                    )}
                  </div>
                </div>

                {latestDeployment && (
                  <div className="project-card__deployment">
                    <StatusBadge status={latestDeployment.status} />
                    {latestDeployment.deploymentUrl && (
                      <span className="project-card__url">
                        <Globe size={12} />
                        {latestDeployment.deploymentUrl}
                      </span>
                    )}
                  </div>
                )}

                <div className="project-card__meta">
                  <span>
                    <GitBranch size={13} />
                    {getFrameworkLabel(project.framework)}
                  </span>
                  <span>
                    <Clock size={13} />
                    {formatDate(project.createdAt)}
                  </span>
                  {project.repositoryUrl && (
                    <span className="project-card__repo">
                      {project.repositoryUrl.replace('https://github.com/', '')}
                    </span>
                  )}
                </div>
              </Link>
            );
          })}
        </div>
      )}
    </Layout>
  );
};

export default Dashboard;

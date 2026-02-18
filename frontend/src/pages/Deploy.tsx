import React, { useState, useEffect, FormEvent } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { ArrowLeft, Rocket, CheckCircle, XCircle, Loader } from 'lucide-react';
import Layout from '../components/Layout';
import StatusBadge from '../components/StatusBadge';
import { projectAPI, deployAPI } from '../services/api';
import { Project, Deployment, DeploymentStatus } from '../types';
import './Deploy.css';

const Deploy: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();

  const [project, setProject] = useState<Project | null>(null);
  const [repoUrl, setRepoUrl] = useState<string>('');
  const [deployment, setDeployment] = useState<Deployment | null>(null);
  const [status, setStatus] = useState<'idle' | 'deploying' | 'polling'>('idle');
  const [error, setError] = useState<string>('');

  useEffect(() => {
    if (!id) return;
    projectAPI.get(id).then((res) => {
      setProject(res.data);
      setRepoUrl(res.data.repositoryUrl || '');
    }).catch(() => {
      setError('Failed to load project.');
    });
  }, [id]);

  // Poll deployment status
  useEffect(() => {
    if (!deployment || status !== 'polling') return;

    const terminal: DeploymentStatus[] = ['BUILD_SUCCESS', 'BUILD_FAILED', 'READY', 'FAILED'];
    if (terminal.includes(deployment.status)) return;

    const interval = setInterval(async () => {
      try {
        const res = await deployAPI.get(deployment.deploymentId);
        setDeployment(res.data);
        if (terminal.includes(res.data.status)) {
          clearInterval(interval);
        }
      } catch {
        clearInterval(interval);
      }
    }, 3000);

    return () => clearInterval(interval);
  }, [deployment, status]);

  const handleDeploy = async (e: FormEvent): Promise<void> => {
    e.preventDefault();
    if (!id) return;
    setError('');
    setStatus('deploying');

    try {
      const res = await deployAPI.create({
        projectId: id,
        repoUrl: repoUrl.trim(),
      });
      setDeployment(res.data);
      setStatus('polling');
    } catch (err: any) {
      setError(err.response?.data?.message || err.response?.data || 'Deployment failed.');
      setStatus('idle');
    }
  };

  const isTerminal = (s: DeploymentStatus): boolean =>
    ['BUILD_SUCCESS', 'BUILD_FAILED', 'READY', 'FAILED'].includes(s);

  const isSuccess = (s: DeploymentStatus): boolean =>
    ['BUILD_SUCCESS', 'READY'].includes(s);

  return (
    <Layout>
      <button className="btn-back" onClick={() => navigate(`/project/${id}`)}>
        <ArrowLeft size={16} />
        Back to Project
      </button>

      <div className="deploy-page">
        <div className="deploy-card">
          <h1 className="deploy-title">
            <Rocket size={24} />
            Deploy {project?.name || 'Project'}
          </h1>

          {!deployment ? (
            <>
              <p className="deploy-subtitle">
                Trigger a new deployment from your Git repository.
              </p>

              {error && <div className="alert alert--error">{error}</div>}

              <form onSubmit={handleDeploy}>
                <div className="form-group">
                  <label className="form-label">Repository URL</label>
                  <input
                    type="url"
                    className="form-input"
                    placeholder="https://github.com/username/repo.git"
                    value={repoUrl}
                    onChange={(e) => setRepoUrl(e.target.value)}
                    required
                  />
                </div>

                <button
                  type="submit"
                  className="btn btn-primary btn-full btn-lg"
                  disabled={status === 'deploying'}
                >
                  {status === 'deploying' ? (
                    <>
                      <span className="spinner" />
                      Deploying...
                    </>
                  ) : (
                    <>
                      <Rocket size={18} />
                      Deploy
                    </>
                  )}
                </button>
              </form>
            </>
          ) : (
            <div className="deploy-status">
              {/* Status Icon */}
              <div className="deploy-status__icon">
                {!isTerminal(deployment.status) ? (
                  <Loader size={48} className="spinning" />
                ) : isSuccess(deployment.status) ? (
                  <CheckCircle size={48} className="icon-success" />
                ) : (
                  <XCircle size={48} className="icon-error" />
                )}
              </div>

              {/* Status Badge */}
              <StatusBadge status={deployment.status} />

              {/* Deployment Info */}
              <div className="deploy-status__info">
                <div className="deploy-info-row">
                  <span className="deploy-info-label">Deployment ID</span>
                  <span className="deploy-info-value mono">
                    {deployment.deploymentId}
                  </span>
                </div>

                {deployment.deploymentUrl && (
                  <div className="deploy-info-row">
                    <span className="deploy-info-label">URL</span>
                    <a
                      href={deployment.deploymentUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="deploy-info-value link"
                    >
                      {deployment.deploymentUrl}
                    </a>
                  </div>
                )}

                {deployment.buildDurationSeconds && (
                  <div className="deploy-info-row">
                    <span className="deploy-info-label">Build Time</span>
                    <span className="deploy-info-value">
                      {deployment.buildDurationSeconds}s
                    </span>
                  </div>
                )}

                {deployment.errorMessage && (
                  <div className="deploy-error-box">
                    {deployment.errorMessage}
                  </div>
                )}
              </div>

              {/* Actions */}
              <div className="deploy-actions">
                {isTerminal(deployment.status) && (
                  <button
                    className="btn btn-primary"
                    onClick={() => {
                      setDeployment(null);
                      setStatus('idle');
                    }}
                  >
                    <Rocket size={16} />
                    Deploy Again
                  </button>
                )}
                <button
                  className="btn"
                  onClick={() => navigate(`/project/${id}`)}
                >
                  View Project
                </button>
              </div>
            </div>
          )}
        </div>
      </div>
    </Layout>
  );
};

export default Deploy;

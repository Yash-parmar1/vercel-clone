import React, { useState, FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import Layout from '../components/Layout';
import { projectAPI } from '../services/api';
import './NewProject.css';

const FRAMEWORKS = [
  { value: 'react', label: 'React' },
  { value: 'next', label: 'Next.js' },
  { value: 'vue', label: 'Vue.js' },
  { value: 'angular', label: 'Angular' },
  { value: 'svelte', label: 'Svelte' },
  { value: 'static', label: 'Static / HTML' },
];

const NewProject: React.FC = () => {
  const navigate = useNavigate();
  const [name, setName] = useState<string>('');
  const [description, setDescription] = useState<string>('');
  const [repositoryUrl, setRepositoryUrl] = useState<string>('');
  const [framework, setFramework] = useState<string>('react');
  const [isPublic, setIsPublic] = useState<boolean>(true);
  const [error, setError] = useState<string>('');
  const [loading, setLoading] = useState<boolean>(false);

  const handleSubmit = async (e: FormEvent): Promise<void> => {
    e.preventDefault();
    setError('');

    if (!name.trim()) {
      setError('Project name is required.');
      return;
    }

    if (!repositoryUrl.trim()) {
      setError('Repository URL is required.');
      return;
    }

    setLoading(true);

    try {
      const res = await projectAPI.create({
        name: name.trim(),
        description: description.trim(),
        repositoryUrl: repositoryUrl.trim(),
        framework,
        isPublic,
      });
      navigate(`/project/${res.data.id}`);
    } catch (err: any) {
      setError(err.response?.data?.message || err.response?.data || 'Failed to create project.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Layout>
      <div className="new-project-page">
        <button className="btn btn-back" onClick={() => navigate('/')}>
          <ArrowLeft size={16} />
          Back
        </button>

        <div className="new-project-card">
          <h1 className="new-project-title">Create a New Project</h1>
          <p className="new-project-subtitle">
            Import a Git repository and deploy it globally.
          </p>

          {error && <div className="alert alert--error">{error}</div>}

          <form onSubmit={handleSubmit}>
            <div className="form-group">
              <label className="form-label" htmlFor="name">Project Name</label>
              <input
                id="name"
                type="text"
                className="form-input"
                placeholder="my-awesome-app"
                value={name}
                onChange={(e) => setName(e.target.value)}
                required
                autoFocus
              />
            </div>

            <div className="form-group">
              <label className="form-label" htmlFor="repo">Git Repository URL</label>
              <input
                id="repo"
                type="url"
                className="form-input"
                placeholder="https://github.com/username/repo.git"
                value={repositoryUrl}
                onChange={(e) => setRepositoryUrl(e.target.value)}
                required
              />
              <span className="form-helper">The public Git repository to deploy from.</span>
            </div>

            <div className="form-group">
              <label className="form-label" htmlFor="framework">Framework</label>
              <select
                id="framework"
                className="form-select"
                value={framework}
                onChange={(e) => setFramework(e.target.value)}
              >
                {FRAMEWORKS.map((fw) => (
                  <option key={fw.value} value={fw.value}>{fw.label}</option>
                ))}
              </select>
            </div>

            <div className="form-group">
              <label className="form-label" htmlFor="description">Description (optional)</label>
              <input
                id="description"
                type="text"
                className="form-input"
                placeholder="A brief description of your project"
                value={description}
                onChange={(e) => setDescription(e.target.value)}
              />
            </div>

            <div className="form-group form-checkbox-group">
              <label className="form-checkbox-label">
                <input
                  type="checkbox"
                  checked={isPublic}
                  onChange={(e) => setIsPublic(e.target.checked)}
                />
                <span>Make this project public</span>
              </label>
            </div>

            <button type="submit" className="btn btn-primary btn-full" disabled={loading}>
              {loading ? <span className="spinner" /> : 'Create & Deploy'}
            </button>
          </form>
        </div>
      </div>
    </Layout>
  );
};

export default NewProject;

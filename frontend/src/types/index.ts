export interface User {
  userId: string;
  email: string;
  username: string;
}

export interface AuthResponse {
  token: string;
  userId: string;
  email: string;
  username: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface SignupRequest {
  username: string;
  email: string;
  password: string;
}

export interface Project {
  id: string;
  name: string;
  description: string;
  repositoryUrl: string;
  framework: string;
  customDomain: string | null;
  isPublic: boolean;
  createdAt: string;
  updatedAt: string;
  deployments: Deployment[];
}

export interface ProjectCreateRequest {
  name: string;
  description: string;
  repositoryUrl: string;
  framework: string;
  isPublic: boolean;
}

export interface ProjectUpdateRequest {
  name?: string;
  description?: string;
  framework?: string;
  customDomain?: string;
  isPublic?: boolean;
}

export interface Deployment {
  deploymentId: string;
  status: DeploymentStatus;
  deploymentUrl: string;
  s3SourcePath: string;
  s3BuildPath: string;
  buildDurationSeconds: number | null;
  errorMessage: string | null;
  createdAt: string;
  completedAt: string | null;
}

export interface DeployRequest {
  projectId: string;
  repoUrl: string;
}

export type DeploymentStatus =
  | 'QUEUED'
  | 'UPLOADING'
  | 'UPLOADED'
  | 'BUILDING'
  | 'BUILD_SUCCESS'
  | 'BUILD_FAILED'
  | 'READY'
  | 'FAILED';

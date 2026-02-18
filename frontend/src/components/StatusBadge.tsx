import React from 'react';
import { DeploymentStatus } from '../types';
import './StatusBadge.css';

interface StatusBadgeProps {
  status: DeploymentStatus;
}

const STATUS_CONFIG: Record<DeploymentStatus, { label: string; className: string }> = {
  QUEUED: { label: 'Queued', className: 'badge--queued' },
  UPLOADING: { label: 'Uploading', className: 'badge--building' },
  UPLOADED: { label: 'Uploaded', className: 'badge--queued' },
  BUILDING: { label: 'Building', className: 'badge--building' },
  BUILD_SUCCESS: { label: 'Built', className: 'badge--success' },
  BUILD_FAILED: { label: 'Failed', className: 'badge--failed' },
  READY: { label: 'Ready', className: 'badge--ready' },
  FAILED: { label: 'Failed', className: 'badge--failed' },
};

const StatusBadge: React.FC<StatusBadgeProps> = ({ status }) => {
  const config = STATUS_CONFIG[status] || { label: status, className: 'badge--queued' };

  return (
    <span className={`badge ${config.className}`}>
      <span className="badge__dot" />
      {config.label}
    </span>
  );
};

export default StatusBadge;

// frontend/src/components/IssueProgressModal.jsx
import React from 'react';

const PERCENT_COMPLETED = 100;

export default function IssueProgressModal({
  progressPercent = 0,
  completedCount = 0,
  totalCount = 0,
}) {
  const isFinished = progressPercent >= PERCENT_COMPLETED;

  let countStatusText = '';
  if (totalCount > 0) {
    if (isFinished) {
      countStatusText = `${completedCount} / ${totalCount} 건 완료`;
    } else {
      countStatusText = `${completedCount} / ${totalCount} 건 처리 중`;
    }
  }

  let mainMessage = (
    <React.Fragment>
      처리 중입니다.
      <br />
      잠시만 기다려주세요.
    </React.Fragment>
  );

  if (isFinished) {
    mainMessage = (
      <React.Fragment>
        생성이 완료되었습니다.
        <br />
        다운로드를 시작합니다.
      </React.Fragment>
    );
  }

  return (
    <div className="progress-modal-overlay">
      <div className="progress-modal-content">
        <div className="progress-modal-text">{mainMessage}</div>
        {totalCount > 0 && (
          <div className="progress-count">{countStatusText}</div>
        )}
        <div className="progress-track">
          <div
            className="progress-fill"
            style={{ width: `${progressPercent}%` }}
          />
        </div>
        <div className="progress-percent">{progressPercent}%</div>
      </div>
    </div>
  );
}

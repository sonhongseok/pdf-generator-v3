// frontend/src/components/HistoryRow.jsx
import React from 'react';

const TOTAL_TABLE_COLUMNS = 7;
const DOWNLOAD_MODE_MERGED = 'MERGED';
const DOWNLOAD_MODE_INDIVIDUAL = 'INDIVIDUAL';

export default function HistoryRow({
    historyItem,
    isExpanded,
    onRowClick,
    onDownload,
    isDownloading,
}) {
    const serialCount = historyItem.serialNos?.length || 0;

    return (
        <React.Fragment>
            <tr
                className={`history-row ${isExpanded ? 'expanded' : ''}`}
                onClick={() => onRowClick(historyItem.id)}
            >
                <td className="col-arrow">
                    <span className={`arrow-icon ${isExpanded ? 'open' : ''}`}>▶</span>
                </td>
                <td>{historyItem.certificateNo}</td>
                <td>{historyItem.certificateDate}</td>
                <td>{historyItem.calibrationDate}</td>
                <td>{historyItem.expiryDate}</td>
                <td>{serialCount}개</td>
                <td>
                    <div className="download-btn-group">
                        <button
                            className="sub-btn download-btn"
                            onClick={(e) =>
                                onDownload(e, historyItem.id, historyItem.certificateNo, DOWNLOAD_MODE_MERGED)
                            }
                            disabled={isDownloading}
                        >
                            {isDownloading ? '생성 중...' : '통합'}
                        </button>
                        <button
                            className="sub-btn download-btn"
                            onClick={(e) =>
                                onDownload(e, historyItem.id, historyItem.certificateNo, DOWNLOAD_MODE_INDIVIDUAL)
                            }
                            disabled={isDownloading}
                        >
                            {isDownloading ? '생성 중...' : '개별(ZIP)'}
                        </button>
                    </div>
                </td>
            </tr>

            {isExpanded && (
                <tr className="serial-detail-row">
                    <td colSpan={TOTAL_TABLE_COLUMNS}>
                        <div className="serial-detail-container">
                            <div className="serial-detail-header">
                                <span className="serial-detail-label">시리얼 번호 목록</span>
                                <span className="serial-detail-count">총 {serialCount}개</span>
                            </div>
                            <div className="serial-chip-list">
                                {historyItem.serialNos?.map((serial, index) => (
                                    <span key={`${historyItem.id}-serial-${index}`} className="serial-chip">
                                        {serial}
                                    </span>
                                ))}
                            </div>
                        </div>
                    </td>
                </tr>
            )}
        </React.Fragment>
    );
}

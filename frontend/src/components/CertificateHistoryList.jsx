// frontend/src/components/CertificateHistoryList.jsx
import React, { useState, useEffect, useRef } from 'react';
import HistoryRow from './HistoryRow';
import HistorySearchBar from './HistorySearchBar';
import HistoryDownloadModal from './HistoryDownloadModal';
import { downloadResultFile } from '../utils/fileDownloader';
import './CertificateHistoryList.css';

const TOTAL_TABLE_COLUMNS = 7;
const POLLING_INTERVAL_MS = 1000;
const MODAL_CLOSE_DELAY_MS = 500;
const STATUS_DONE = 'DONE';
const STATUS_FAILED = 'FAILED';

export default function CertificateHistoryList() {
    const [histories, setHistories] = useState([]);
    const [loading, setLoading] = useState(true);
    const [errorMessage, setErrorMessage] = useState(null);
    const [downloadingId, setDownloadingId] = useState(null);
    const [progressPercent, setProgressPercent] = useState(0);
    const [completedCount, setCompletedCount] = useState(0);
    const [totalCount, setTotalCount] = useState(0);
    const [expandedId, setExpandedId] = useState(null);
    const [searchTerm, setSearchTerm] = useState('');

    const pollingRef = useRef(null);

    useEffect(() => {
        fetchHistories();
        return () => {
            if (pollingRef.current) {
                clearInterval(pollingRef.current);
            }
        };
    }, []);

    async function fetchHistories() {
        try {
            setLoading(true);
            setErrorMessage(null);
            const response = await fetch('/api/documents/certificates');
            if (!response.ok) {
                throw new Error('이력 데이터를 불러오는데 실패했습니다.');
            }
            const data = await response.json();
            setHistories(data);
        } catch (err) {
            setErrorMessage(err.message || '이력 데이터를 불러오는 중 오류가 발생했습니다.');
        } finally {
            setLoading(false);
        }
    }

    async function handleDownload(event, id, certificateNo, mode) {
        event.stopPropagation();
        if (pollingRef.current) {
            clearInterval(pollingRef.current);
        }

        try {
            setDownloadingId(id);
            setProgressPercent(0);
            setCompletedCount(0);
            setTotalCount(0);

            const requestResponse = await fetch(`/api/documents/certificates/${id}/download/request?mode=${mode}`, {
                method: 'POST',
            });
            if (!requestResponse.ok) {
                const errData = await requestResponse.json();
                throw new Error(errData.message || '재다운로드 작업 요청에 실패했습니다.');
            }

            const { jobId } = await requestResponse.json();

            pollingRef.current = setInterval(async () => {
                try {
                    const statusResponse = await fetch(`/api/documents/certificates/pdf/status/${jobId}`);
                    if (!statusResponse.ok) return;

                    const jobData = await statusResponse.json();
                    setProgressPercent(jobData.progressPercent || 0);
                    setCompletedCount(jobData.completedCount || 0);
                    setTotalCount(jobData.totalCount || 0);

                    if (jobData.status === STATUS_DONE) {
                        clearInterval(pollingRef.current);
                        pollingRef.current = null;
                        setProgressPercent(100);

                        await downloadResultFile(jobId, certificateNo, mode);

                        setTimeout(() => {
                            setDownloadingId(null);
                            setProgressPercent(0);
                            setCompletedCount(0);
                            setTotalCount(0);
                        }, MODAL_CLOSE_DELAY_MS);
                    } else if (jobData.status === STATUS_FAILED) {
                        clearInterval(pollingRef.current);
                        pollingRef.current = null;
                        alert(jobData.errorMessage || '재발급 처리에 실패했습니다.');
                        setDownloadingId(null);
                    }
                } catch (pollErr) {
                    // 일시적 통신 지연 시 폴링 유지
                }
            }, POLLING_INTERVAL_MS);
        } catch (err) {
            alert(err.message || '다운로드 요청 중 오류가 발생했습니다.');
            setDownloadingId(null);
        }
    }

    function handleRowClick(id) {
        setExpandedId((prevId) => (prevId === id ? null : id));
    }

    if (loading) {
        return <div className="history-status">로딩 중...</div>;
    }

    if (errorMessage) {
        return <div className="history-error">{errorMessage}</div>;
    }

    const filteredHistories = histories.filter((historyItem) => {
        if (!searchTerm) return true;
        const lowerSearch = searchTerm.toLowerCase();
        if (historyItem.certificateNo?.toLowerCase().includes(lowerSearch)) return true;
        if (historyItem.serialNos?.some((serial) => serial && serial.toLowerCase().includes(lowerSearch))) return true;
        return false;
    });

    return (
        <div className="history-container">
            <HistorySearchBar searchTerm={searchTerm} onSearchChange={setSearchTerm} />

            {histories.length === 0 ? (
                <div className="history-status">발급된 성적서 이력이 없습니다.</div>
            ) : (
                <div className="table-wrapper">
                    <table className="history-table">
                        <thead>
                            <tr>
                                <th className="col-arrow"></th>
                                <th>발급 번호</th>
                                <th>발급일</th>
                                <th>교정일</th>
                                <th>만료일</th>
                                <th>
                                    시리얼
                                    <br />
                                    개수
                                </th>
                                <th className="col-download">다운로드</th>
                            </tr>
                        </thead>
                        <tbody>
                            {filteredHistories.length === 0 ? (
                                <tr>
                                    <td colSpan={TOTAL_TABLE_COLUMNS} className="history-empty-cell">
                                        검색 결과가 없습니다.
                                    </td>
                                </tr>
                            ) : (
                                filteredHistories.map((historyItem) => (
                                    <HistoryRow
                                        key={historyItem.id}
                                        historyItem={historyItem}
                                        isExpanded={expandedId === historyItem.id}
                                        onRowClick={handleRowClick}
                                        onDownload={handleDownload}
                                        isDownloading={downloadingId === historyItem.id}
                                    />
                                ))
                            )}
                        </tbody>
                    </table>
                </div>
            )}

            {downloadingId && (
                <HistoryDownloadModal
                    progressPercent={progressPercent}
                    completedCount={completedCount}
                    totalCount={totalCount}
                />
            )}
        </div>
    );
}

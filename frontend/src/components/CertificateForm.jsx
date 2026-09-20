// frontend/src/components/CertificateForm.jsx
import React, { useState } from 'react';
import axios from 'axios';
import ExcelUploadButton from './ExcelUploadButton';
import '../App.css';

export default function CertificateForm() {
  function getTodayString() {
    const today = new Date();
    const year = today.getFullYear();
    const month = String(today.getMonth() + 1).padStart(2, '0');
    const day = String(today.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  function getOneYearLater() {
    const date = new Date();
    date.setFullYear(date.getFullYear() + 1);
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  const [certificateDateText, setCertificateDateText] = useState(getTodayString());
  const [calibrationDateText, setCalibrationDateText] = useState(getTodayString());
  const [expiryDateText, setExpiryDateText] = useState(getOneYearLater());
  const [serialNoText, setSerialNoText] = useState('');
  // 선택입력: 시작 시퀀스 번호 (4자리, 비어있으면 0001로 자동 적용)
  const [startSeqText, setStartSeqText] = useState('');

  const [isSubmitting, setIsSubmitting] = useState(false);
  const [progressPercent, setProgressPercent] = useState(0);
  const [completedCount, setCompletedCount] = useState(0);
  const [totalCount, setTotalCount] = useState(0);
  const [statusMessage, setStatusMessage] = useState('');
  const [errorMessage, setErrorMessage] = useState('');
  // 생성 방식: 'MERGED'(기본값, 통합 PDF) 또는 'INDIVIDUAL'(개별 PDF ZIP)
  const [generateMode, setGenerateMode] = useState('MERGED');

  const normalizeDate = (text) => {
    if (!text) return '';
    const clean = text.replace(/[^0-9]/g, '');
    if (clean.length === 8) {
      return `${clean.substring(0, 4)}-${clean.substring(4, 6)}-${clean.substring(6, 8)}`;
    }
    return text;
  };

  const handleCalibrationDateChange = (e) => {
    const newVal = e.target.value;
    setCalibrationDateText(newVal);

    const norm = normalizeDate(newVal);
    const datePattern = /^\d{4}-\d{2}-\d{2}$/;
    if (datePattern.test(norm)) {
      const date = new Date(norm);
      if (!isNaN(date.getTime())) {
        date.setFullYear(date.getFullYear() + 1);
        const year = date.getFullYear();
        const month = String(date.getMonth() + 1).padStart(2, '0');
        const day = String(date.getDate()).padStart(2, '0');
        setExpiryDateText(`${year}-${month}-${day}`);
      }
    }
  };

  const handleExit = () => {
    if (window.confirm("프로그램을 종료하시겠습니까?")) {
      window.close();
    }
  };

  const handleExcelUploadSuccess = (serialNumbers, count) => {
    setSerialNoText(serialNumbers.join(' '));
    setStatusMessage(`Success: 엑셀에서 ${count}개의 Serial No를 불러왔습니다.`);
    setErrorMessage('');
  };

  const handleExcelUploadError = (errorMessageText) => {
    setErrorMessage(errorMessageText);
    setStatusMessage('Error: Excel upload failed');
  };

  const handleSave = async (event) => {
    if (event) {
      event.preventDefault();
    }

    const rawCertDate = certificateDateText.trim();
    const rawCalDate = calibrationDateText.trim();
    const rawExpDate = expiryDateText.trim();
    const rawSerials = serialNoText.trim();

    if (!rawCertDate) {
      setErrorMessage('Certificate Date를 입력해 주세요.');
      setStatusMessage('Error: Missing Certificate Date');
      return;
    }
    if (!rawCalDate) {
      setErrorMessage('Calibration Date를 입력해 주세요.');
      setStatusMessage('Error: Missing Calibration Date');
      return;
    }
    if (!rawExpDate) {
      setErrorMessage('Expiry Date를 입력해 주세요.');
      setStatusMessage('Error: Missing Expiry Date');
      return;
    }
    if (!rawSerials) {
      setErrorMessage('Serial No를 입력해 주세요.');
      setStatusMessage('Error: Missing Serial No');
      return;
    }

    const normCertDate = normalizeDate(rawCertDate);
    const normCalDate = normalizeDate(rawCalDate);
    const normExpDate = normalizeDate(rawExpDate);

    const datePattern = /^\d{4}-\d{2}-\d{2}$/;
    if (!datePattern.test(normCertDate) || !datePattern.test(normCalDate) || !datePattern.test(normExpDate)) {
      setErrorMessage('날짜 형식이 올바르지 않습니다. (예: 2026-05-21 또는 20260521)');
      setStatusMessage('Error: Invalid Date Format');
      return;
    }

    if (new Date(normCalDate) > new Date(normExpDate) || new Date(normCertDate) > new Date(normExpDate)) {
      setErrorMessage('만료일은 발행일/교정일보다 빠를 수 없습니다.');
      setStatusMessage('Error: Invalid Date Range');
      return;
    }

    const serialList = rawSerials.split(/\s+/).filter(Boolean);
    if (serialList.length === 0) {
      setErrorMessage('최소 하나 이상의 Serial No가 필요합니다.');
      setStatusMessage('Error: Empty Serial List');
      return;
    }

    const uniqueSerials = new Set(serialList);
    if (uniqueSerials.size !== serialList.length) {
      setErrorMessage('입력된 Serial No 중에 중복된 값이 있습니다. 중복 없이 입력해 주세요.');
      setStatusMessage('Error: Duplicate Serial No');
      return;
    }

    // 시작 시퀀스 번호 유효성 검사 및 오버플로우 검사
    const rawStartSeq = startSeqText.trim();
    const startSeqList = rawStartSeq !== '' ? rawStartSeq.split(/\s+/) : [];
    
    if (startSeqList.length > 1 && startSeqList.length !== serialList.length) {
      setErrorMessage(`여러 개의 Start No를 입력한 경우, Serial No 개수와 정확히 일치해야 합니다. (입력: ${startSeqList.length}개, 시리얼: ${serialList.length}개)`);
      setStatusMessage('Error: Start No Count Mismatch');
      return;
    }

    const FOUR_DIGIT_PATTERN = /^\d{4}$/;
    for (const seq of startSeqList) {
      const num = parseInt(seq, 10);
      if (!FOUR_DIGIT_PATTERN.test(seq) || num < 1 || num > 9999) {
        setErrorMessage('Start No는 0001~9999 범위의 4자리 숫자여야 합니다.');
        setStatusMessage('Error: Invalid Start No');
        return;
      }
    }

    if (startSeqList.length <= 1) {
      let startSeqNum = startSeqList.length === 1 ? parseInt(startSeqList[0], 10) : 1;
      if (startSeqNum + serialList.length - 1 > 9999) {
        setErrorMessage(`시리얼 개수가 너무 많아 Certificate NO 시퀀스가 9999를 초과합니다. (예상 종료 번호: ${startSeqNum + serialList.length - 1})`);
        setStatusMessage('Error: Start No Overflow');
        return;
      }
    }

    // 발급 전 사용자 최종 확인 (이 단계에서 확인하면 DB 저장까지 한 번에 처리됨)
    const serialSummary = serialList.join(', ');
    const confirmed = window.confirm(
      `아래 내용으로 성적서를 발급하시겠습니까?\n\n` +
      `발행일: ${normCertDate}\n` +
      `교정일: ${normCalDate}\n` +
      `만료일: ${normExpDate}\n` +
      `시리얼: ${serialSummary}\n\n` +
      `[확인]을 누르면 발급 이력이 즉시 저장됩니다.`
    );
    if (!confirmed) return;

    setIsSubmitting(true);
    setProgressPercent(0);
    setCompletedCount(0);
    setTotalCount(serialList.length);
    setErrorMessage('');
    setStatusMessage('PDF 생성 요청 중...');

    try {
      const payload = {
        certificateDate: normCertDate,
        calibrationDate: normCalDate,
        expiryDate: normExpDate,
        serialNos: serialList,
        generateMode: generateMode,
        ...(rawStartSeq !== '' && { startSequenceNo: rawStartSeq })
      };

      // 1단계: 비동기 작업 등록 후 jobId 즉시 수신
      const requestResponse = await axios.post('/api/documents/certificates/pdf/request', payload);
      const jobId = requestResponse.data.jobId;

      setStatusMessage('PDF 생성 중...');

      // 2단계: 2초 간격으로 진행률 폴링
      await pollJobStatus(jobId, normCertDate, rawStartSeq);

    } catch (error) {
      console.error(error);
      let errMsg = 'PDF 생성에 실패했습니다.';
      if (error.response && error.response.data) {
        const data = error.response.data;
        errMsg = (typeof data === 'object' ? data.message : data) || errMsg;
      }
      setErrorMessage(errMsg);
      setStatusMessage('Error: Operation failed');
      setIsSubmitting(false);
      setProgressPercent(0);
    }
  };

  /**
   * jobId의 생성 상태를 폴링하고, 완료 시 파일을 다운로드합니다.
   */
  const pollJobStatus = (jobId, normCertDate, rawStartSeq) => {
    return new Promise((resolve, reject) => {
      const POLLING_INTERVAL_MS = 2000;

      const intervalId = setInterval(async () => {
        try {
          const statusResponse = await axios.get(
            `/api/documents/certificates/pdf/status/${jobId}`
          );
          const { status, progressPercent: serverPercent, completedCount: done, totalCount: total, errorMessage: errMsg } = statusResponse.data;

          setProgressPercent(serverPercent);
          setCompletedCount(done);
          setTotalCount(total);

          if (status === 'DONE') {
            clearInterval(intervalId);
            await downloadJobResult(jobId, normCertDate, rawStartSeq);
            resolve();
          } else if (status === 'FAILED') {
            clearInterval(intervalId);
            setErrorMessage(errMsg || 'PDF 생성에 실패했습니다.');
            setStatusMessage('Error: Operation failed');
            setIsSubmitting(false);
            setProgressPercent(0);
            resolve();
          }
          // PENDING / RUNNING 상태는 계속 폴링
        } catch (error) {
          clearInterval(intervalId);
          reject(error);
        }
      }, POLLING_INTERVAL_MS);
    });
  };

  /**
   * 완료된 작업의 파일을 다운로드합니다.
   */
  const downloadJobResult = async (jobId, normCertDate, rawStartSeq) => {
    const isIndividualMode = generateMode === 'INDIVIDUAL';
    const mimeType = isIndividualMode ? 'application/zip' : 'application/pdf';

    const downloadResponse = await axios.get(
      `/api/documents/certificates/pdf/download/${jobId}`,
      { responseType: 'blob' }
    );

    // 파일명 결정: 백엔드 Content-Disposition 우선, 없으면 프론트엔드 생성
    const resolvedStartSeq = rawStartSeq !== '' ? parseInt(rawStartSeq, 10) : 1;
    const certNoFormatted = `OP${normCertDate.replace(/-/g, '')}${String(resolvedStartSeq).padStart(4, '0')}`;
    let downloadFilename = `${certNoFormatted}${isIndividualMode ? '.zip' : '.pdf'}`;

    const contentDisposition = downloadResponse.headers['content-disposition'];
    if (contentDisposition) {
      const filenameMatch = contentDisposition.match(/filename="?([^";]+)"?/);
      if (filenameMatch && filenameMatch[1]) {
        downloadFilename = filenameMatch[1];
      }
    }

    const blobUrl = window.URL.createObjectURL(new Blob([downloadResponse.data], { type: mimeType }));
    const anchor = document.createElement('a');
    anchor.href = blobUrl;
    anchor.setAttribute('download', downloadFilename);
    document.body.appendChild(anchor);
    anchor.click();
    document.body.removeChild(anchor);
    window.URL.revokeObjectURL(blobUrl);

    setProgressPercent(100);
    setStatusMessage(`Success: [${certNoFormatted}] 발급 완료.`);
    setTimeout(() => {
      setIsSubmitting(false);
      setProgressPercent(0);
      setCompletedCount(0);
      setTotalCount(0);
    }, 500);
  };

  return (
    <div className="client-area">
      <div className="form-grid">
        <div className="label-cell">Certificate Date</div>
        <div className="input-cell">
          <input
            type="text"
            value={certificateDateText}
            onChange={(e) => setCertificateDateText(e.target.value)}
            placeholder="YYYY-MM-DD"
            className="classic-input"
          />
        </div>

        <div className="label-cell">Calibration Date</div>
        <div className="input-cell">
          <input
            type="text"
            value={calibrationDateText}
            onChange={handleCalibrationDateChange}
            placeholder="YYYY-MM-DD"
            className="classic-input"
          />
        </div>

        <div className="label-cell">Expiry Date</div>
        <div className="input-cell">
          <input
            type="text"
            value={expiryDateText}
            onChange={(e) => setExpiryDateText(e.target.value)}
            placeholder="YYYY-MM-DD"
            className="classic-input"
          />
        </div>

        <div className="label-cell">Serial No</div>
        <div className="input-cell" style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
          <input
            type="text"
            value={serialNoText}
            onChange={(e) => setSerialNoText(e.target.value)}
            placeholder="공백으로 구분하여 입력"
            className="classic-input"
            style={{ flex: 1 }}
          />
          <ExcelUploadButton
            onUploadSuccess={handleExcelUploadSuccess}
            onError={handleExcelUploadError}
            disabled={isSubmitting}
          />
        </div>

        <div className="label-cell">생성 방식</div>
        <div className="input-cell">
          <label style={{ marginRight: '16px', cursor: 'pointer' }}>
            <input
              type="radio"
              name="generateMode"
              value="MERGED"
              checked={generateMode === 'MERGED'}
              onChange={(e) => setGenerateMode(e.target.value)}
              style={{ marginRight: '4px' }}
            />
            통합 PDF
          </label>
          <label style={{ cursor: 'pointer' }}>
            <input
              type="radio"
              name="generateMode"
              value="INDIVIDUAL"
              checked={generateMode === 'INDIVIDUAL'}
              onChange={(e) => setGenerateMode(e.target.value)}
              style={{ marginRight: '4px' }}
            />
            개별 PDF (ZIP)
          </label>
        </div>

        <div className="label-cell">
          Start No
          <span style={{ fontSize: '10px', color: 'var(--vh-text-muted)', display: 'block' }}>(0001~9999, 선택)</span>
        </div>
        <div className="input-cell">
          <input
            type="text"
            value={startSeqText}
            onChange={(e) => setStartSeqText(e.target.value)}
            placeholder="비워두면 0001로 자동 적용"
            className="classic-input"
          />
        </div>
      </div>

      {!isSubmitting && statusMessage && !errorMessage && (
        <div className="success-bubble">
          <strong>Info:</strong> {statusMessage}
        </div>
      )}

      {isSubmitting && (
        <div className="progress-modal-overlay">
          <div className="progress-modal-content">
            <div className="progress-modal-text">처리 중입니다. <br />잠시만 기다려주세요.</div>
            {totalCount > 0 && (
              <div className="progress-count">{completedCount} / {totalCount} 건 완료</div>
            )}
            <div className="progress-track">
              <div className="progress-fill" style={{ width: `${progressPercent}%` }} />
            </div>
            <div className="progress-percent">{progressPercent}%</div>
          </div>
        </div>
      )}

      {errorMessage && (
        <div className="error-bubble">
          <strong>Warning:</strong> {errorMessage}
        </div>
      )}

      <div className="button-area">
        <button onClick={handleSave} disabled={isSubmitting} className="main-btn">
          {isSubmitting ? '생성 중...' : '성적서 생성'}
        </button>
        <button onClick={handleExit} className="sub-btn">
          종료
        </button>
      </div>
    </div>
  );
}
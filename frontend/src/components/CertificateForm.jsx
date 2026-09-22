// src/components/CertificateForm.jsx
import React, { useState } from 'react';
import CertificateFormFields from './CertificateFormFields';
import IssueProgressModal from './IssueProgressModal';
import useCertificateIssue from '../hooks/useCertificateIssue';
import {
  getTodayString,
  getOneYearLater,
  normalizeDate,
  validateCertificateInputs
} from '../utils/certificateFormUtils';
import '../App.css';

const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;

export default function CertificateForm() {
  const [certificateDateText, setCertificateDateText] = useState(getTodayString());
  const [calibrationDateText, setCalibrationDateText] = useState(getTodayString());
  const [expiryDateText, setExpiryDateText] = useState(getOneYearLater());
  const [serialNoText, setSerialNoText] = useState('');
  const [startSeqText, setStartSeqText] = useState('');
  const [generateMode, setGenerateMode] = useState('MERGED');

  const {
    isSubmitting,
    progressPercent,
    completedCount,
    totalCount,
    statusMessage,
    errorMessage,
    setCustomErrorMessage,
    setCustomStatusMessage,
    executeCertificateIssue
  } = useCertificateIssue();

  const handleCalibrationDateChange = (newVal) => {
    setCalibrationDateText(newVal);
    const norm = normalizeDate(newVal);
    if (DATE_PATTERN.test(norm)) {
      const parsedDate = new Date(norm);
      if (!isNaN(parsedDate.getTime())) {
        parsedDate.setFullYear(parsedDate.getFullYear() + 1);
        const year = parsedDate.getFullYear();
        const month = String(parsedDate.getMonth() + 1).padStart(2, '0');
        const day = String(parsedDate.getDate()).padStart(2, '0');
        setExpiryDateText(`${year}-${month}-${day}`);
      }
    }
  };

  const handleExcelUploadSuccess = (serialNumbers, count) => {
    setSerialNoText(serialNumbers.join(' '));
    setCustomStatusMessage(`Success: 총 ${count}건의 Serial No를 불러왔습니다.`);
    setCustomErrorMessage('');
  };

  const handleExcelUploadError = (errorMessageText) => {
    setCustomErrorMessage(errorMessageText);
    setCustomStatusMessage('Error: Excel upload failed');
  };

  const handleExit = () => {
    if (window.confirm('프로그램을 종료하시겠습니까?')) {
      window.close();
    }
  };

  const handleSave = async (event) => {
    if (event) event.preventDefault();

    const validationResult = validateCertificateInputs({
      certificateDateText,
      calibrationDateText,
      expiryDateText,
      serialNoText,
      startSeqText
    });

    if (!validationResult.isValid) {
      setCustomErrorMessage(validationResult.errorMessage);
      setCustomStatusMessage(validationResult.statusMessage);
      return;
    }

    const { normCertDate, normCalDate, normExpDate, serialList, rawStartSeq } = validationResult.data;
    const serialSummary = serialList.join(', ');
    const confirmed = window.confirm(
      `아래 내용으로 성적서를 발급하시겠습니까?\n\n` +
      `발행일: ${normCertDate}\n검사일: ${normCalDate}\n만료일: ${normExpDate}\n시리얼: ${serialSummary}\n\n` +
      `[확인]을 누르면 발급 이력이 저장됩니다.`
    );
    if (!confirmed) return;

    await executeCertificateIssue({
      normCertDate,
      normCalDate,
      normExpDate,
      serialList,
      generateMode,
      rawStartSeq
    });
  };

  const shouldShowSuccess = !isSubmitting && statusMessage && !errorMessage;

  return (
    <div className="client-area">
      <CertificateFormFields
        certificateDateText={certificateDateText}
        onCertificateDateChange={setCertificateDateText}
        calibrationDateText={calibrationDateText}
        onCalibrationDateChange={handleCalibrationDateChange}
        expiryDateText={expiryDateText}
        onExpiryDateChange={setExpiryDateText}
        serialNoText={serialNoText}
        onSerialNoChange={setSerialNoText}
        generateMode={generateMode}
        onGenerateModeChange={setGenerateMode}
        startSeqText={startSeqText}
        onStartSeqChange={setStartSeqText}
        onExcelUploadSuccess={handleExcelUploadSuccess}
        onExcelUploadError={handleExcelUploadError}
        isSubmitting={isSubmitting}
      />

      {shouldShowSuccess && (
        <div className="success-bubble">
          <strong>Info:</strong> {statusMessage}
        </div>
      )}

      {isSubmitting && (
        <IssueProgressModal
          progressPercent={progressPercent}
          completedCount={completedCount}
          totalCount={totalCount}
        />
      )}

      {errorMessage && (
        <div className="error-bubble">
          <strong>Warning:</strong> {errorMessage}
        </div>
      )}

      <div className="button-area">
        <button onClick={handleSave} disabled={isSubmitting} className="main-btn">
          {isSubmitting ? '발급 중...' : '저장 및 발급'}
        </button>
        <button onClick={handleExit} className="sub-btn">
          종료
        </button>
      </div>
    </div>
  );
}

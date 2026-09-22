// src/components/CertificateFormFields.jsx
import React from 'react';
import ExcelUploadButton from './ExcelUploadButton';

export default function CertificateFormFields({
  certificateDateText,
  onCertificateDateChange,
  calibrationDateText,
  onCalibrationDateChange,
  expiryDateText,
  onExpiryDateChange,
  serialNoText,
  onSerialNoChange,
  generateMode,
  onGenerateModeChange,
  startSeqText,
  onStartSeqChange,
  onExcelUploadSuccess,
  onExcelUploadError,
  isSubmitting
}) {
  return (
    <div className="form-grid">
      <div className="label-cell">Certificate Date</div>
      <div className="input-cell">
        <input
          type="text"
          value={certificateDateText}
          onChange={(event) => onCertificateDateChange(event.target.value)}
          placeholder="YYYY-MM-DD"
          className="classic-input"
          disabled={isSubmitting}
        />
      </div>

      <div className="label-cell">Calibration Date</div>
      <div className="input-cell">
        <input
          type="text"
          value={calibrationDateText}
          onChange={(event) => onCalibrationDateChange(event.target.value)}
          placeholder="YYYY-MM-DD"
          className="classic-input"
          disabled={isSubmitting}
        />
      </div>

      <div className="label-cell">Expiry Date</div>
      <div className="input-cell">
        <input
          type="text"
          value={expiryDateText}
          onChange={(event) => onExpiryDateChange(event.target.value)}
          placeholder="YYYY-MM-DD"
          className="classic-input"
          disabled={isSubmitting}
        />
      </div>

      <div className="label-cell">Serial No</div>
      <div className="input-cell" style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
        <input
          type="text"
          value={serialNoText}
          onChange={(event) => onSerialNoChange(event.target.value)}
          placeholder="공백으로 구분하여 입력"
          className="classic-input"
          style={{ flex: 1 }}
          disabled={isSubmitting}
        />
        <ExcelUploadButton
          onUploadSuccess={onExcelUploadSuccess}
          onError={onExcelUploadError}
          disabled={isSubmitting}
        />
      </div>

      <div className="label-cell">생성 방식</div>
      <div className="input-cell">
        <label style={{ marginRight: '16px', cursor: isSubmitting ? 'not-allowed' : 'pointer' }}>
          <input
            type="radio"
            name="generateMode"
            value="MERGED"
            checked={generateMode === 'MERGED'}
            onChange={(event) => onGenerateModeChange(event.target.value)}
            style={{ marginRight: '4px' }}
            disabled={isSubmitting}
          />
          통합 PDF
        </label>
        <label style={{ cursor: isSubmitting ? 'not-allowed' : 'pointer' }}>
          <input
            type="radio"
            name="generateMode"
            value="INDIVIDUAL"
            checked={generateMode === 'INDIVIDUAL'}
            onChange={(event) => onGenerateModeChange(event.target.value)}
            style={{ marginRight: '4px' }}
            disabled={isSubmitting}
          />
          개별 PDF (ZIP)
        </label>
      </div>

      <div className="label-cell">
        Start No
        <span style={{ fontSize: '10px', color: 'var(--vh-text-muted)', display: 'block' }}>
          (0001~9999, 선택)
        </span>
      </div>
      <div className="input-cell">
        <input
          type="text"
          value={startSeqText}
          onChange={(event) => onStartSeqChange(event.target.value)}
          placeholder="비워두면 0001로 자동 적용"
          className="classic-input"
          disabled={isSubmitting}
        />
      </div>
    </div>
  );
}

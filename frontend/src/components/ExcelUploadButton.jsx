// frontend/src/components/ExcelUploadButton.jsx
import React, { useRef, useState } from 'react';
import axios from 'axios';

const EXCEL_UPLOAD_API_URL = '/api/documents/certificates/parse-excel';
const ALLOWED_EXTENSIONS = ['.xlsx', '.xls'];

export default function ExcelUploadButton({ onUploadSuccess, onError, disabled }) {
  const fileInputRef = useRef(null);
  const [isUploading, setIsUploading] = useState(false);

  const handleButtonClick = () => {
    if (fileInputRef.current) {
      fileInputRef.current.click();
    }
  };

  const handleFileChange = async (event) => {
    const selectedFile = event.target.files?.[0];
    if (!selectedFile) {
      return;
    }

    const lowerCaseName = selectedFile.name.toLowerCase();
    const isValidExtension = ALLOWED_EXTENSIONS.some((ext) => lowerCaseName.endsWith(ext));
    if (!isValidExtension) {
      onError('엑셀 파일(.xlsx, .xls)만 업로드 가능합니다.');
      if (fileInputRef.current) {
        fileInputRef.current.value = '';
      }
      return;
    }

    const formData = new FormData();
    formData.append('file', selectedFile);

    setIsUploading(true);
    try {
      const response = await axios.post(EXCEL_UPLOAD_API_URL, formData, {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
      });

      const { serialNos, count } = response.data;
      if (Array.isArray(serialNos) && serialNos.length > 0) {
        onUploadSuccess(serialNos, count);
      } else {
        onError('엑셀 파일에서 유효한 Serial No를 찾지 못했습니다.');
      }
    } catch (error) {
      console.error('Excel upload error:', error);
      let errorMessageText = '엑셀 파일 업로드에 실패했습니다.';
      if (error.response && error.response.data) {
        const errorData = error.response.data;
        errorMessageText = (typeof errorData === 'object' ? errorData.message : errorData) || errorMessageText;
      }
      onError(errorMessageText);
    } finally {
      setIsUploading(false);
      if (fileInputRef.current) {
        fileInputRef.current.value = '';
      }
    }
  };

  const buttonText = isUploading ? '읽는 중...' : '엑셀 불러오기';

  return (
    <>
      <input
        type="file"
        ref={fileInputRef}
        onChange={handleFileChange}
        accept=".xlsx, .xls"
        style={{ display: 'none' }}
      />
      <button
        type="button"
        onClick={handleButtonClick}
        disabled={disabled || isUploading}
        className="classic-button"
        style={{
          padding: '4px 10px',
          fontSize: '12px',
          whiteSpace: 'nowrap',
          cursor: disabled || isUploading ? 'not-allowed' : 'pointer',
        }}
        title="A열에 Serial No가 나열된 엑셀(.xlsx, .xls) 파일을 업로드합니다."
      >
        {buttonText}
      </button>
    </>
  );
}
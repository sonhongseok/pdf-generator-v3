// src/hooks/useCertificateIssue.js
import { useState } from 'react';
import axios from 'axios';

const POLLING_INTERVAL_MS = 2000;
const RESET_DELAY_MS = 500;
const DEFAULT_START_SEQUENCE = 1;
const SEQUENCE_PAD_LENGTH = 4;

export default function useCertificateIssue() {
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [progressPercent, setProgressPercent] = useState(0);
  const [completedCount, setCompletedCount] = useState(0);
  const [totalCount, setTotalCount] = useState(0);
  const [statusMessage, setStatusMessage] = useState('');
  const [errorMessage, setErrorMessage] = useState('');

  const clearMessages = () => {
    setStatusMessage('');
    setErrorMessage('');
  };

  const setCustomErrorMessage = (messageText) => {
    setErrorMessage(messageText);
  };

  const setCustomStatusMessage = (messageText) => {
    setStatusMessage(messageText);
  };

  const pollJobStatus = (jobId, normCertDate, rawStartSeq, generateMode) => {
    return new Promise((resolve, reject) => {
      const intervalId = setInterval(async () => {
        try {
          const statusResponse = await axios.get(
            `/api/documents/certificates/pdf/status/${jobId}`
          );
          const {
            status,
            progressPercent: serverPercent,
            completedCount: doneCount,
            totalCount: serverTotalCount,
            errorMessage: serverErrorMessage
          } = statusResponse.data;

          setProgressPercent(serverPercent);
          setCompletedCount(doneCount);
          setTotalCount(serverTotalCount);

          if (status === 'DONE') {
            clearInterval(intervalId);
            await downloadJobResult(jobId, normCertDate, rawStartSeq, generateMode);
            resolve();
          } else if (status === 'FAILED') {
            clearInterval(intervalId);
            const failureMessage = serverErrorMessage || 'PDF 생성 작업에 실패했습니다.';
            setErrorMessage(failureMessage);
            setStatusMessage('Error: Operation failed');
            setIsSubmitting(false);
            setProgressPercent(0);
            resolve();
          }
        } catch (pollingError) {
          clearInterval(intervalId);
          reject(pollingError);
        }
      }, POLLING_INTERVAL_MS);
    });
  };

  const downloadJobResult = async (jobId, normCertDate, rawStartSeq, generateMode) => {
    try {
      const isIndividualMode = generateMode === 'INDIVIDUAL';
      const mimeType = isIndividualMode ? 'application/zip' : 'application/pdf';

      const downloadResponse = await axios.get(
        `/api/documents/certificates/pdf/download/${jobId}`,
        { responseType: 'blob' }
      );

      let resolvedStartSeq = DEFAULT_START_SEQUENCE;
      if (rawStartSeq !== '') {
        resolvedStartSeq = parseInt(rawStartSeq, 10);
      }

      const formattedSequence = String(resolvedStartSeq).padStart(SEQUENCE_PAD_LENGTH, '0');
      const dateWithoutHyphen = normCertDate.replace(/-/g, '');
      const certNoFormatted = `OP${dateWithoutHyphen}${formattedSequence}`;

      let downloadFilename = `${certNoFormatted}.pdf`;
      if (isIndividualMode) {
        downloadFilename = `${certNoFormatted}.zip`;
      }

      const contentDisposition = downloadResponse.headers['content-disposition'];
      if (contentDisposition) {
        const filenameMatch = contentDisposition.match(/filename="?([^";]+)"?/);
        if (filenameMatch && filenameMatch[1]) {
          downloadFilename = filenameMatch[1];
        }
      }

      const blobData = new Blob([downloadResponse.data], { type: mimeType });
      const blobUrl = window.URL.createObjectURL(blobData);
      const downloadAnchor = document.createElement('a');
      downloadAnchor.href = blobUrl;
      downloadAnchor.setAttribute('download', downloadFilename);
      document.body.appendChild(downloadAnchor);
      downloadAnchor.click();
      document.body.removeChild(downloadAnchor);
      window.URL.revokeObjectURL(blobUrl);

      setProgressPercent(100);
      setStatusMessage(`Success: [${certNoFormatted}] 발급이 완료되었습니다.`);
      setTimeout(() => {
        setIsSubmitting(false);
        setProgressPercent(0);
        setCompletedCount(0);
        setTotalCount(0);
      }, RESET_DELAY_MS);
    } catch (downloadError) {
      console.error(downloadError);
      setErrorMessage('발급 파일 다운로드 중 오류가 발생했습니다.');
      setStatusMessage('Error: Download failed');
      setIsSubmitting(false);
    }
  };

  const executeCertificateIssue = async ({
    normCertDate,
    normCalDate,
    normExpDate,
    serialList,
    generateMode,
    rawStartSeq
  }) => {
    setIsSubmitting(true);
    setProgressPercent(0);
    setCompletedCount(0);
    setTotalCount(serialList.length);
    setErrorMessage('');
    setStatusMessage('PDF 발급 요청 중...');

    try {
      const payload = {
        certificateDate: normCertDate,
        calibrationDate: normCalDate,
        expiryDate: normExpDate,
        serialNos: serialList,
        generateMode: generateMode
      };
      if (rawStartSeq !== '') {
        payload.startSequenceNo = rawStartSeq;
      }

      const requestResponse = await axios.post('/api/documents/certificates/pdf/request', payload);
      const jobId = requestResponse.data.jobId;

      setStatusMessage('PDF 생성 중...');
      await pollJobStatus(jobId, normCertDate, rawStartSeq, generateMode);
    } catch (requestError) {
      console.error(requestError);
      let failureReason = 'PDF 요청 처리에 실패했습니다.';
      if (requestError.response && requestError.response.data) {
        const responseData = requestError.response.data;
        if (typeof responseData === 'object' && responseData.message) {
          failureReason = responseData.message;
        } else if (typeof responseData === 'string') {
          failureReason = responseData;
        }
      }
      setErrorMessage(failureReason);
      setStatusMessage('Error: Operation failed');
      setIsSubmitting(false);
      setProgressPercent(0);
    }
  };

  return {
    isSubmitting,
    progressPercent,
    completedCount,
    totalCount,
    statusMessage,
    errorMessage,
    clearMessages,
    setCustomErrorMessage,
    setCustomStatusMessage,
    executeCertificateIssue
  };
}

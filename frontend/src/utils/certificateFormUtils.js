// src/utils/certificateFormUtils.js

const MAXIMUM_SEQUENCE_NUMBER = 9999;
const MINIMUM_SEQUENCE_NUMBER = 1;
const DATE_FORMAT_LENGTH = 8;
const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;
const FOUR_DIGIT_PATTERN = /^\d{4}$/;

export function getTodayString() {
  const today = new Date();
  const year = today.getFullYear();
  const month = String(today.getMonth() + 1).padStart(2, '0');
  const day = String(today.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

export function getOneYearLater() {
  const targetDate = new Date();
  targetDate.setFullYear(targetDate.getFullYear() + 1);
  const year = targetDate.getFullYear();
  const month = String(targetDate.getMonth() + 1).padStart(2, '0');
  const day = String(targetDate.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

export function normalizeDate(rawText) {
  if (!rawText) return '';
  const digitsOnly = rawText.replace(/[^0-9]/g, '');
  if (digitsOnly.length === DATE_FORMAT_LENGTH) {
    return `${digitsOnly.substring(0, 4)}-${digitsOnly.substring(4, 6)}-${digitsOnly.substring(6, 8)}`;
  }
  return rawText;
}

export function validateCertificateInputs({
  certificateDateText,
  calibrationDateText,
  expiryDateText,
  serialNoText,
  startSeqText
}) {
  const rawCertDate = certificateDateText.trim();
  const rawCalDate = calibrationDateText.trim();
  const rawExpDate = expiryDateText.trim();
  const rawSerials = serialNoText.trim();

  if (!rawCertDate || !rawCalDate || !rawExpDate || !rawSerials) {
    return {
      isValid: false,
      errorMessage: '필수 입력 항목(날짜 및 Serial No)을 모두 입력해 주세요.',
      statusMessage: 'Error: Missing Required Fields'
    };
  }

  const normCertDate = normalizeDate(rawCertDate);
  const normCalDate = normalizeDate(rawCalDate);
  const normExpDate = normalizeDate(rawExpDate);

  if (!DATE_PATTERN.test(normCertDate) || !DATE_PATTERN.test(normCalDate) || !DATE_PATTERN.test(normExpDate)) {
    return {
      isValid: false,
      errorMessage: '날짜 형식이 올바르지 않습니다. (예: 2026-05-21 또는 20260521)',
      statusMessage: 'Error: Invalid Date Format'
    };
  }

  if (new Date(normCalDate) > new Date(normExpDate) || new Date(normCertDate) > new Date(normExpDate)) {
    return {
      isValid: false,
      errorMessage: '만료일은 발행일/검사일보다 이후여야 합니다.',
      statusMessage: 'Error: Invalid Date Range'
    };
  }

  const serialList = rawSerials.split(/\s+/).filter(Boolean);
  if (serialList.length === 0) {
    return {
      isValid: false,
      errorMessage: '최소 하나 이상의 Serial No가 필요합니다.',
      statusMessage: 'Error: Empty Serial List'
    };
  }

  const uniqueSerials = new Set(serialList);
  if (uniqueSerials.size !== serialList.length) {
    return {
      isValid: false,
      errorMessage: '입력된 Serial No 중에 중복된 값이 있습니다. 중복을 제거해 주세요.',
      statusMessage: 'Error: Duplicate Serial No'
    };
  }

  const rawStartSeq = startSeqText.trim();
  const startSeqList = rawStartSeq !== '' ? rawStartSeq.split(/\s+/) : [];

  if (startSeqList.length > 1 && startSeqList.length !== serialList.length) {
    return {
      isValid: false,
      errorMessage: `개별 Start No를 입력할 경우, Serial No 개수와 정확히 일치해야 합니다. (입력: ${startSeqList.length}개, 시리얼: ${serialList.length}개)`,
      statusMessage: 'Error: Start No Count Mismatch'
    };
  }

  for (const seq of startSeqList) {
    const num = parseInt(seq, 10);
    if (!FOUR_DIGIT_PATTERN.test(seq) || num < MINIMUM_SEQUENCE_NUMBER || num > MAXIMUM_SEQUENCE_NUMBER) {
      return {
        isValid: false,
        errorMessage: 'Start No는 0001~9999 사이의 4자리 숫자여야 합니다.',
        statusMessage: 'Error: Invalid Start No'
      };
    }
  }

  if (startSeqList.length <= 1) {
    const startSeqNum = startSeqList.length === 1 ? parseInt(startSeqList[0], 10) : 1;
    if (startSeqNum + serialList.length - 1 > MAXIMUM_SEQUENCE_NUMBER) {
      return {
        isValid: false,
        errorMessage: `시리얼 개수가 너무 많아 Certificate NO 번호가 9999를 초과합니다. (마지막 번호: ${startSeqNum + serialList.length - 1})`,
        statusMessage: 'Error: Start No Overflow'
      };
    }
  }

  return {
    isValid: true,
    data: { normCertDate, normCalDate, normExpDate, serialList, rawStartSeq }
  };
}

// frontend/test_certificate_form_utils.js
import assert from 'node:assert';
import {
  getTodayString,
  getOneYearLater,
  normalizeDate,
  validateCertificateInputs
} from './src/utils/certificateFormUtils.js';

console.log('=== [1] 프론트엔드 날짜 함수 단위 테스트 ===');

// 1. getTodayString 형식 검증
const today = getTodayString();
assert.match(today, /^\d{4}-\d{2}-\d{2}$/, '오늘 날짜 형식은 YYYY-MM-DD여야 함');
console.log('PASS: getTodayString() ->', today);

// 2. getOneYearLater 형식 및 1년 차이 검증
const oneYearLater = getOneYearLater();
assert.match(oneYearLater, /^\d{4}-\d{2}-\d{2}$/, '1년 뒤 날짜 형식은 YYYY-MM-DD여야 함');
const todayYear = parseInt(today.substring(0, 4), 10);
const nextYear = parseInt(oneYearLater.substring(0, 4), 10);
assert.strictEqual(nextYear, todayYear + 1, '1년 뒤 연도는 올해보다 1 커야 함');
console.log('PASS: getOneYearLater() ->', oneYearLater);

// 3. normalizeDate 정규화 검증
assert.strictEqual(normalizeDate('20260521'), '2026-05-21', '8자리 숫자는 하이픈 형태로 변환');
assert.strictEqual(normalizeDate('2026-05-21'), '2026-05-21', '기존 하이픈 형식 유지');
assert.strictEqual(normalizeDate(''), '', '빈 값 처리');
console.log('PASS: normalizeDate() 정상 동작');

console.log('\n=== [2] 입력값 유효성 검증(validateCertificateInputs) 세밀 테스트 ===');

// 시나리오 1: 정상 케이스
const validCase = validateCertificateInputs({
  certificateDateText: '2026-05-21',
  calibrationDateText: '2026-05-20',
  expiryDateText: '2027-05-20',
  serialNoText: '280A66 280BAE 28177E',
  startSeqText: '0001'
});
assert.strictEqual(validCase.isValid, true, '정상 입력은 유효해야 함');
assert.strictEqual(validCase.data.serialList.length, 3, '시리얼은 3개로 분리되어야 함');
console.log('PASS 시나리오 1: 정상 입력 검증 통과');

// 시나리오 2: 필수값 누락 (시리얼 누락)
const missingSerialCase = validateCertificateInputs({
  certificateDateText: '2026-05-21',
  calibrationDateText: '2026-05-20',
  expiryDateText: '2027-05-20',
  serialNoText: '   ',
  startSeqText: ''
});
assert.strictEqual(missingSerialCase.isValid, false);
console.log('PASS 시나리오 2: 필수 시리얼 누락 방어 통과');

// 시나리오 3: 날짜 형식 오류
const invalidDateCase = validateCertificateInputs({
  certificateDateText: '2026-05-XX',
  calibrationDateText: '2026-05-20',
  expiryDateText: '2027-05-20',
  serialNoText: '280A66',
  startSeqText: ''
});
assert.strictEqual(invalidDateCase.isValid, false);
console.log('PASS 시나리오 3: 잘못된 날짜 형식 방어 통과');

// 시나리오 4: 날짜 역전 (만료일이 검사일보다 이전)
const reversedDateCase = validateCertificateInputs({
  certificateDateText: '2026-05-21',
  calibrationDateText: '2026-05-20',
  expiryDateText: '2026-05-19',
  serialNoText: '280A66',
  startSeqText: ''
});
assert.strictEqual(reversedDateCase.isValid, false);
assert.match(reversedDateCase.errorMessage, /만료일/);
console.log('PASS 시나리오 4: 만료일 역전 방어 통과');

// 시나리오 5: 중복 시리얼 감지
const duplicateSerialCase = validateCertificateInputs({
  certificateDateText: '2026-05-21',
  calibrationDateText: '2026-05-20',
  expiryDateText: '2027-05-20',
  serialNoText: '280A66 280BAE 280A66',
  startSeqText: ''
});
assert.strictEqual(duplicateSerialCase.isValid, false);
assert.match(duplicateSerialCase.errorMessage, /중복/);
console.log('PASS 시나리오 5: 중복 시리얼 번호 감지 및 방어 통과');

// 시나리오 6: 개별 Start No 개수 불일치
const mismatchedSeqCase = validateCertificateInputs({
  certificateDateText: '2026-05-21',
  calibrationDateText: '2026-05-20',
  expiryDateText: '2027-05-20',
  serialNoText: '280A66 280BAE',
  startSeqText: '0001 0002 0003'
});
assert.strictEqual(mismatchedSeqCase.isValid, false);
assert.match(mismatchedSeqCase.errorMessage, /일치/);
console.log('PASS 시나리오 6: 개별 Start No 개수 불일치 방어 통과');

// 시나리오 7: 잘못된 Start No 형식 (숫자 아님 또는 범위 초과)
const invalidSeqCase = validateCertificateInputs({
  certificateDateText: '2026-05-21',
  calibrationDateText: '2026-05-20',
  expiryDateText: '2027-05-20',
  serialNoText: '280A66',
  startSeqText: 'ABCD'
});
assert.strictEqual(invalidSeqCase.isValid, false);
console.log('PASS 시나리오 7: 숫자 아닌 Start No 방어 통과');

// 시나리오 8: 9999 오버플로우 방어
const overflowCase = validateCertificateInputs({
  certificateDateText: '2026-05-21',
  calibrationDateText: '2026-05-20',
  expiryDateText: '2027-05-20',
  serialNoText: 'SN1 SN2 SN3',
  startSeqText: '9998'
});
assert.strictEqual(overflowCase.isValid, false);
assert.match(overflowCase.errorMessage, /9999를 초과/);
console.log('PASS 시나리오 8: 9999 시퀀스 오버플로우 방어 통과');

// 시나리오 9: 비워둔 Start No 자동 1 기본 적용
const defaultSeqCase = validateCertificateInputs({
  certificateDateText: '2026-05-21',
  calibrationDateText: '2026-05-20',
  expiryDateText: '2027-05-20',
  serialNoText: 'SN1 SN2',
  startSeqText: ''
});
assert.strictEqual(defaultSeqCase.isValid, true);
assert.strictEqual(defaultSeqCase.data.rawStartSeq, '');
console.log('PASS 시나리오 9: Start No 빈 값 허용 통과');

console.log('\n>>> 모든 프론트엔드 단위 테스트 12건 100% 통과 <<<');

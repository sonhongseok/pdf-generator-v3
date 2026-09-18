
# c:/work/pdf-generator-v3/README.md 를 UTF-8 without BOM 으로 저장

content = r"""# 📋 OP Certificate Generator
### 성적서 자동 발행 시스템 V3

> MS Word 기반 성적서 양식(`.docx`)을 활용하여 대량의 PDF 성적서를 초고속으로 자동 생성하고,
> 발급 이력을 안전하게 관리하는 **데스크톱 전용 독립 실행(Standalone) 솔루션**입니다.

![Java](https://img.shields.io/badge/Java-17_LTS-ED8B00?style=flat-square&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.2.5-6DB33F?style=flat-square&logo=springboot&logoColor=white)
![React](https://img.shields.io/badge/React-18.3.1-61DAFB?style=flat-square&logo=react&logoColor=black)
![Vite](https://img.shields.io/badge/Vite-5.2.11-646CFF?style=flat-square&logo=vite&logoColor=white)
![Apache POI](https://img.shields.io/badge/Apache_POI-5.2.5-D22128?style=flat-square)

---

## 📑 목차
- [핵심 기능 요약](#1-핵심-기능-요약)
- [시스템 아키텍처 및 기술 스택](#2-시스템-아키텍처-및-기술-스택)
- [핵심 비즈니스 로직](#3-핵심-비즈니스-로직-심층-해설)
- [개발 환경 구성 및 로컬 실행](#4-개발-환경-구성-및-로컬-실행-가이드)
- [프로그램 사용 가이드 (3분 퀵스타트)](#5-프로그램-사용-가이드-3분-퀵스타트)
- [원클릭 빌드 및 배포 자동화](#6-원클릭-빌드-및-배포-자동화)
- [프로젝트 폴더 구조](#7-프로젝트-폴더-구조)
- [주요 트러블슈팅 이력](#8-주요-트러블슈팅-이력)

---

## 1. 핵심 기능 요약

| 아이콘 | 기능 | 핵심 특징 | 사용자 혜택 |
|:---:|---|---|---|
| 📊 | **엑셀 시리얼 자동 추출** | A열 Serial No 및 한글/영문/모델명 헤더 스마트 판별 | 수백 개 시리얼 번호 수기 입력 불필요 |
| ⚡ | **비동기 고속 생성** | 백그라운드 작업 큐 + 2초 주기 실시간 프로그레스 바 | 브라우저 멈춤·타임아웃 없이 진행 상황 시각적 확인 |
| 🚀 | **Word 배치 최적화** | 단일 Word 인스턴스 연속 일괄 변환 (`.vbs`) | 대량 변환 시간 기존 대비 **70% 이상 단축** |
| 📦 | **무설치 Standalone** | `jpackage` 내장 경량 JRE 탑재, 원클릭 실행 | Java · Node.js · MySQL 설치 없는 완전 독립 구동 |
| 💾 | **경량 JSON DB** | 원자적 동시성 제어(`Lock`) + 자가 치유(Self-Healing) | DB 설치 없이 단일 파일(`history.json`)로 안전 관리 |

---

## 2. 시스템 아키텍처 및 기술 스택

### 2-1. 시스템 계층 구조도

아래 다이어그램은 사용자의 브라우저 요청이 어떤 경로로 처리되어 최종 PDF 파일이 생성되는지를 계층별로 표현합니다.

```mermaid
graph TB
    subgraph UI ["🖥️  Presentation Layer — React SPA"]
        A1["📝 CertificateForm\n날짜·시리얼 입력 폼"]
        A2["📊 ExcelUploadButton\n엑셀 파일 업로드 버튼"]
        A3["⏳ ProgressModal\n실시간 진행률 모달"]
    end

    subgraph API ["⚙️  Application Layer — Spring Boot 3 REST API"]
        B1["CertificateController\nPOST /api/certificates/generate"]
        B2["CertificateExcelController\nPOST /api/certificates/parse-excel"]
        B3["PdfJobController\nGET /pdf/status · /pdf/download"]
    end

    subgraph BIZ ["💼  Business Layer — 핵심 비즈니스 로직"]
        C1["CertificateExcelService\n헤더 스마트 판별 및 시리얼 파싱"]
        C2["DocxTemplateService\npoi-tl 변수 치환 및 후처리"]
        C3["MsWordPdfConverter\nWord PDF 배치 변환 및 병합"]
        C4["CertificateHistoryService\n채번·중복 방어·이력 저장"]
        C5["PdfJobService\n비동기 작업 큐 관리"]
    end

    subgraph INFRA ["💾  Infrastructure Layer — 외부 의존성"]
        D1["Apache POI 5.2.5\n엑셀 .xlsx/.xls 파싱"]
        D2["MS Word COM + docx2pdf_batch.vbs\n배치 PDF 변환 엔진"]
        D3["Apache PDFBox 3.0.2\nPDF 병합 처리"]
        D4[("history.json\n파일 기반 JSON DB")]
        D5["certificate_template.docx\nWord 성적서 원본 템플릿"]
    end

    UI -->|HTTP / REST API| API
    API --> BIZ
    BIZ --> INFRA

    style UI fill:#e8f4fd,stroke:#1a7bc4,stroke-width:2px
    style API fill:#e8f9e8,stroke:#2d8a4e,stroke-width:2px
    style BIZ fill:#fff8e1,stroke:#f9a825,stroke-width:2px
    style INFRA fill:#fce4ec,stroke:#c62828,stroke-width:2px
```

> [!NOTE]
> **Standalone 구조의 핵심**: 이 솔루션은 외부 데이터베이스(MySQL 등) 없이 `history.json` 단일 파일로 모든 발급 이력을 관리합니다. 동시 접근 시에도 Java `ReentrantLock`으로 원자성을 보장합니다.

---

### 2-2. 상세 기술 스택 (Tech Stack)

#### 🖥️ 프런트엔드

| 기술 / 라이브러리 | 버전 | 역할 및 선정 사유 |
|---|:---:|---|
| **React** | 18.3.1 | 컴포넌트 기반 UI 구성 및 실시간 진행률 상태 관리 |
| **Vite** | 5.2.11 | 초고속 HMR 개발 서버 및 최적화된 정적 자산 번들링 |
| **Axios** | 1.6.8 | 백엔드 비동기 API 통신 및 파일 다운로드 Blob 처리 |

#### ⚙️ 백엔드

| 기술 / 라이브러리 | 버전 | 역할 및 선정 사유 |
|---|:---:|---|
| **Spring Boot** | 3.2.5 | 경량 REST API 서버 및 비동기 작업(`@Async`) 스레드풀 제공 |
| **Java** | 17 (LTS) | `jpackage` 임베디드 런타임 호환, 최신 언어 기능 활용 |
| **poi-tl** | 1.12.1 | Word 템플릿 내 태그(`{cno}`, `{sno}` 등) 정밀 치환 |
| **Apache POI** | 5.2.5 | `.xlsx` / `.xls` A열 시리얼 번호 파싱 |
| **Apache PDFBox** | 3.0.2 | 개별 성적서 PDF를 1개의 통합 문서로 무손실 병합 |
| **MS Word + VBScript** | - | 서식 깨짐 없는 원본 품질의 Word-to-PDF 배치 변환 |
| **파일 기반 JSON DB** | - | 외부 RDBMS 없이 동시성 락이 적용된 경량 스토리지 |

#### 📦 빌드 및 배포

| 기술 | 버전 | 역할 |
|---|:---:|---|
| **Maven (mvnw)** | 내장 래퍼 | 백엔드 의존성 관리 및 실행 가능 JAR 패키징 |
| **jpackage** | JDK 17 | JRE를 내장한 단독 실행 데스크톱 앱(`.exe`) 패키징 |
| **PowerShell** | 5.1+ | 원클릭 빌드 자동화 스크립트(`build.ps1`) 실행 환경 |

---

## 3. 핵심 비즈니스 로직 심층 해설

### 3-1. 📊 엑셀 스마트 파싱 알고리즘 (`CertificateExcelService`)

엑셀 A열에서 시리얼 번호를 추출할 때, **헤더 행이 있는 파일**과 **1행부터 데이터가 시작되는 파일** 모두를 오류 없이 처리하는 알고리즘입니다.

#### 판별 흐름도

```mermaid
flowchart TD
    START(["📂 엑셀 파일 업로드"]) --> READ["Sheet 0 의 A열 1행 셀 값 읽기"]
    READ --> NORMALIZE["소문자 변환 + 특수문자 제거\n예: 'Serial No.' → 'serialno'"]
    NORMALIZE --> CHECK_KEYWORD{"정제된 값이\n헤더 키워드 목록에 해당?"}

    CHECK_KEYWORD -->|"YES\n예: serial, 시리얼, no, a10 등"| SKIP["⏭️ 1행 건너뜀 (헤더로 판정)\n2행부터 데이터 추출 시작"]
    CHECK_KEYWORD -->|"NO\n예: 2813C4, 28017F 등 실제 시리얼"| INCLUDE["✅ 1행 포함 (데이터로 판정)\n1행부터 데이터 추출 시작"]

    SKIP --> FILTER["빈 셀 및 공백 행 필터링"]
    INCLUDE --> FILTER
    FILTER --> FORMAT["숫자 셀 타입 → DataFormatter 적용\n10001.0 또는 1.0E+04 → '10001'"]
    FORMAT --> RESULT(["✨ 시리얼 번호 배열 반환"])
```

#### 헤더 판별 키워드 목록

| 분류 | 인식되는 키워드 (정제 후) |
|---|---|
| **영문 일반** | `serial`, `serialno`, `serialnumber`, `sn`, `s/n`, `no` |
| **한글 일반** | `시리얼`, `시리얼번호`, `시리얼넘버`, `번호` |
| **모델명 접두사** | `a10`, `a10시리얼`, `a10시리얼번호` |
| **접미사 패턴** | `~시리얼`, `~시리얼번호` 로 끝나는 모든 복합 헤더 |

> [!TIP]
> **실제 시리얼 번호 보존 원리**: `2813C4`, `28017F` 같은 6자리 영숫자 제품 시리얼은 위 키워드 목록에 해당하지 않으므로 **1행부터 100% 그대로 추출**됩니다. 헤더 여부 판단은 반드시 정제된 값 기준으로 엄격하게 비교합니다.

---

### 3-2. ⚡ 비동기 작업 큐 & 2초 주기 폴링 메커니즘 (`PdfJobService`)

100건 이상의 성적서 생성 시 브라우저 연결 타임아웃 문제를 해결하기 위해 구축한 비동기 파이프라인입니다.

#### 전체 처리 흐름

```mermaid
sequenceDiagram
    actor 사용자
    participant React as 🖥️ React UI
    participant API as ⚙️ Spring Boot API
    participant Queue as 📋 비동기 작업 큐

    사용자->>React: 성적서 생성 버튼 클릭
    React->>API: POST /pdf/request (시리얼 목록 전송)
    API-->>React: 즉시 응답 { jobId: "uuid-xxxx" } ← 0.1초
    API->>Queue: @Async 스레드풀에 작업 등록 (백그라운드 시작)

    loop 2초마다 반복 폴링
        React->>API: GET /pdf/status/{jobId}
        API-->>React: { status: RUNNING, progressPercent: 65, completedCount: 65, totalCount: 100 }
        React->>사용자: 프로그레스 바 및 카운터 실시간 업데이트
    end

    Queue-->>API: 작업 완료 (status: DONE)
    React->>API: GET /pdf/download/{jobId}
    API-->>React: PDF 파일 Blob 전송
    React->>사용자: 브라우저 자동 저장 다이얼로그
    Note over API, Queue: 다운로드 완료 후 인메모리 큐에서 자동 제거
```

> [!NOTE]
> `POST /pdf/request` 요청이 **0.1초 이내에 즉시 반환**되므로 브라우저가 응답을 기다리며 멈추는 현상이 발생하지 않습니다. 무거운 처리는 전부 서버 백그라운드에서 진행됩니다.

---

### 3-3. 🚀 MS Word 배치 변환 속도 최적화 (`docx2pdf_batch.vbs`)

`.docx` → `.pdf` 변환 시 MS Word 프로세스 기동 오버헤드를 근본적으로 제거한 배치 방식입니다.

#### 기존 방식 vs V3 배치 방식 비교

```mermaid
graph LR
    subgraph OLD ["❌ 기존 방식 — 파일마다 Word 재기동"]
        O1["📄 파일 1"] --> OW1["Word 실행"] --> OP1["변환"] --> OC1["Word 종료"]
        O2["📄 파일 2"] --> OW2["Word 실행"] --> OP2["변환"] --> OC2["Word 종료"]
        O3["📄 ... 100개"] --> OW3["Word 실행 x100"] --> OP3["변환 x100"] --> OC3["Word 종료 x100"]
    end

    subgraph NEW ["✅ V3 배치 방식 — Word 단 1회 기동"]
        N1["Word 단 1회 실행"] --> NF["📄 파일 1 변환\n📄 파일 2 변환\n📄 ... 100개 연속 변환"] --> NC["Word 1회 종료"]
    end
```

| 구분 | 기존 방식 | V3 배치 방식 | 개선 효과 |
|:---:|---|---|:---:|
| **Word 기동 횟수** | 파일 수만큼 (100회) | 단 1회 | **99% 감소** |
| **파일당 변환 시간** | 2 ~ 3초 | 0.3 ~ 0.5초 | **~83% 단축** |
| **100개 총 소요 시간** | 약 250초 (4분 이상) | 약 40~50초 | **70% 이상 절감** |

---

### 3-4. 🔢 발급 번호 자동 채번 및 4중 중복 방어 규칙

#### 채번 포맷

```
OP + 발행일(YYYYMMDD) + 시퀀스(4자리)
        ↓
  OP202609160001   ← 2026년 09월 16일 첫 번째 발급
  OP202609160002   ← 같은 날 두 번째 발급
       ...
  OP202609169999   ← 하루 최대 9,999건
```

#### 4중 복합키 중복 방어 체계

| 방어 단계 | 검사 항목 | 차단 조건 |
|:---:|---|---|
| **1차** | 발행일 (Certificate Date) | 동일 발행일 내 이력 조회 |
| **2차** | 교정일 (Calibration Date) | 1차 통과 후 교정일 일치 확인 |
| **3차** | 만료일 (Expiry Date) | 2차 통과 후 만료일 일치 확인 |
| **4차** | 시리얼 번호 목록 전체 | 3차 통과 후 시리얼 배열 100% 동일 시 차단 |

> [!WARNING]
> **시퀀스 오버플로우 방어**: 하루 발급 시작 번호 + 시리얼 개수의 합이 **9,999를 초과**하면, 명확한 한글 경고 메시지를 표시하고 즉시 작업을 중단합니다. 번호 중복 발생을 원천 차단합니다.

---

## 4. 개발 환경 구성 및 로컬 실행 가이드

### 4-1. 사전 요구 사양

> [!IMPORTANT]
> 아래 소프트웨어가 모두 설치되어 있어야 로컬 개발 환경을 정상적으로 구동할 수 있습니다.

| 소프트웨어 | 권장 버전 | 필수 여부 | 역할 |
|---|:---:|:---:|---|
| **JDK** | OpenJDK / Oracle JDK 17+ | ✅ 필수 | 백엔드 빌드 및 실행, jpackage 패키징 |
| **Node.js** | v18.x 이상 (LTS) | ✅ 필수 | 프런트엔드 패키지 관리 및 Vite 빌드 |
| **npm** | v9.x 이상 | ✅ 필수 | 프런트엔드 의존성 설치 |
| **MS Word** | Microsoft Office 2016+ | ✅ 필수 | `.docx` → `.pdf` 배치 변환 엔진 구동 |

### 4-2. 로컬 개발 서버 실행 순서

**① 프런트엔드 개발 서버 구동**

```bash
cd frontend
npm install       # 최초 1회만 실행
npm run dev       # 개발 서버 시작
```

- 접속 주소: `http://localhost:5173`
- `/api/*` 요청은 `vite.config.js` 프록시 설정에 의해 백엔드(`http://localhost:8080`)로 자동 전달됩니다.

**② 백엔드 개발 서버 구동**

```bash
cd backend
./mvnw spring-boot:run    # Windows: mvnw.cmd spring-boot:run
```

- 접속 주소: `http://localhost:8080`
- 로컬 실행 시 템플릿 경로(`../certificate_template.docx`)와 데이터 폴더(`../data`)를 프로젝트 루트 기준으로 참조합니다.

**③ 백엔드 단위 테스트 실행**

```bash
cd backend
./mvnw test
```

현재 **총 13건의 테스트가 전체 통과** 상태입니다.

| 테스트 클래스 | 건수 | 주요 검증 항목 |
|---|:---:|---|
| `CertificateExcelServiceTest` | 7건 | 헤더 제외, 실데이터 보존, 숫자 서식, 빈 행 필터, 빈 파일 방어 |
| `CertificateExcelControllerTest` | 2건 | HTTP 200 정상 업로드, HTTP 400 잘못된 파일 |
| `CertificateControllerTest` | 4건 | 성적서 발급 파이프라인 통합 테스트 |

---

## 5. 프로그램 사용 가이드 (3분 퀵스타트)

> [!NOTE]
> 아래 가이드는 **최종 배포된 실행 파일** 사용자를 위한 설명입니다. Java나 Node.js 설치 없이 바로 사용할 수 있습니다.

### 실행부터 PDF 저장까지 — 5단계

```
STEP 1          STEP 2             STEP 3              STEP 4            STEP 5
  ↓               ↓                  ↓                   ↓                 ↓
[EXE 실행]  →  [날짜 확인]  →  [시리얼 입력]  →  [출력 방식 선택]  →  [생성 클릭]
```

| 단계 | 작업 내용 | 상세 설명 |
|:---:|---|---|
| **STEP 1** | `OP_Certificate_Generator.exe` 실행 | 더블 클릭 시 주소창 없는 전용 윈도우 UI 자동 실행 |
| **STEP 2** | 날짜 3종 확인 | Certificate Date, Calibration Date, Expiry Date (오늘 날짜 및 1년 뒤 만료일 자동 계산) |
| **STEP 3-A** | 엑셀로 시리얼 입력 | `[엑셀 불러오기]` 클릭 → A열에 시리얼이 적힌 `.xlsx` / `.xls` 파일 선택 |
| **STEP 3-B** | 직접 시리얼 입력 | 텍스트창에 시리얼 번호를 공백(스페이스)으로 구분하여 직접 입력 |
| **STEP 4-A** | 출력: 통합 PDF | 모든 시리얼이 1개의 PDF 문서로 합쳐져 다운로드 |
| **STEP 4-B** | 출력: 개별 PDF (ZIP) | 시리얼마다 1장씩 생성, ZIP으로 압축 다운로드 |
| **STEP 5** | `[성적서 생성]` 클릭 | 실시간 진행률 모달 표시 후 작업 완료 시 파일 자동 저장 |

---

## 6. 원클릭 빌드 및 배포 자동화

프로젝트 루트에서 아래 명령어 한 줄이면 프런트엔드 빌드 → 백엔드 JAR 패키징 → JRE 번들링 → ZIP 압축까지 모두 자동으로 완료됩니다.

```powershell
powershell -ExecutionPolicy Bypass -File .\build.ps1
```

> [!WARNING]
> **빌드 전 필수 확인**: `OP_Certificate_Generator.exe`가 실행 중이면 내부 JAR 파일에 잠금(Lock)이 걸려 빌드가 실패합니다. **반드시 앱을 종료한 후** 빌드를 시작하세요.

### 빌드 파이프라인 단계별 상세

```mermaid
graph LR
    S1["STEP 1\n🎨 React 빌드\nnpm run build"] -->
    S2["STEP 2\n📁 정적 파일 병합\nfrontend/dist\n→ backend/resources/static"] -->
    S3["STEP 3\n📦 Maven 패키징\nmvnw clean package\n→ .jar 생성"] -->
    S4["STEP 4\n🖥️ jpackage 이미지\nJRE 내장 앱 이미지\n→ dist/ 생성"] -->
    S5["STEP 5\n🗂️ 리소스 복사\ntemplate.docx\ndocx2pdf_batch.vbs"] -->
    S6["STEP 6\n🗜️ ZIP 압축\nOP_Certificate_Generator_v3.zip"]
```

| 단계 | 작업 내용 | 주요 결과물 |
|:---:|---|---|
| **STEP 1** | React 소스 Vite 프로덕션 빌드 | `frontend/dist/` |
| **STEP 2** | 웹 번들을 백엔드 정적 디렉토리로 병합 | `backend/src/main/resources/static/` |
| **STEP 3** | Maven으로 웹 내장형 실행 JAR 생성 | `backend/target/pdf-generator-backend-1.0.0.jar` |
| **STEP 4** | `jpackage`로 경량 JRE 포함 앱 이미지 생성 | `dist/OP_Certificate_Generator/` |
| **STEP 5** | Word 템플릿, VBS 스크립트 복사 및 충돌 DLL 정리 | 배포용 디렉토리 구성 완료 |
| **STEP 6** | 최종 배포 디렉토리 전체 ZIP 압축 | `dist/OP_Certificate_Generator_v3.zip` |

---

## 7. 프로젝트 폴더 구조

```
pdf-generator-v3/
│
├── 📄 certificate_template.docx          # 성적서 MS Word 원본 템플릿
├── 🔧 build.ps1                          # 원클릭 빌드 자동화 스크립트
├── 📖 README.md                          # 개발자 대상 개발 설명서 (현재 문서)
│
├── 📁 frontend/                          # React + Vite 프런트엔드
│   ├── src/
│   │   ├── components/
│   │   │   ├── CertificateForm.jsx       # 날짜·시리얼 입력 메인 폼
│   │   │   ├── ExcelUploadButton.jsx     # 엑셀 파일 업로드 버튼 컴포넌트
│   │   │   └── ProgressModal.jsx         # 실시간 진행률 모달
│   │   └── App.jsx
│   ├── package.json
│   └── vite.config.js                    # /api 프록시 → localhost:8080
│
└── 📁 backend/                           # Spring Boot 백엔드
    ├── pom.xml                           # Maven 의존성 관리
    └── src/main/java/com/example/pdfgen/
        ├── controller/
        │   ├── CertificateController.java         # 성적서 발급 REST API
        │   └── CertificateExcelController.java    # 엑셀 파싱 REST API
        ├── service/
        │   ├── CertificateExcelService.java       # 헤더 스마트 판별 및 파싱
        │   ├── DocxTemplateService.java            # poi-tl 템플릿 변수 치환
        │   ├── MsWordPdfConverter.java             # Word PDF 배치 변환
        │   ├── PdfJobService.java                  # 비동기 작업 큐 관리
        │   └── CertificateHistoryService.java      # 채번·중복 방어·이력 저장
        └── dto/
            └── CertificateRequest.java             # API 요청 바디 DTO
```

---

## 8. 주요 트러블슈팅 이력

| 분류 | 🔴 문제 현상 | 🔍 원인 | ✅ 해결책 |
|---|---|---|---|
| **엑셀 업로드 (V3.2)** | 한글 헤더(`시리얼번호`)가 시리얼 번호로 오인 입력됨 | 헤더 키워드 파일 인코딩 손상 (CP949 오염) | UTF-8 without BOM 복원 + 접미사(`~시리얼`) 스마트 판별 도입 |
| **성능 최적화** | 대량 PDF 생성 시 변환 속도 극심한 저하 | 파일마다 MS Word 프로세스 실행/종료 반복 | `docx2pdf_batch.vbs` 단일 프로세스 연속 배치 변환으로 70% 단축 |
| **사용자 경험 (V3.1)** | 100건 이상 생성 시 브라우저 타임아웃 발생 | 단일 동기 HTTP 요청의 처리 시간 한계 | 비동기 백그라운드 작업 큐 + 2초 주기 폴링 프로그레스 바 구축 |
| **인코딩 안정화** | 밸리데이션 경고창에 외계어·물음표(`??`, `?리??`) 표시 | Windows 콘솔 기본 인코딩(CP949) 불일치 | 백엔드/프런트엔드 전체 UTF-8 without BOM 표준화 적용 |
| **무설치 환경 (V3.0)** | 배포 대상 PC에 Java, MySQL 사전 설치 필요 | 외부 JRE 및 RDBMS 의존성 구조 | `jpackage` Standalone 구조 + 파일 기반 `JsonDataStore` 전환 |
| **창 충돌 방지** | Chrome이 열려 있을 때 앱 실행 실패 | Chrome 기본 프로필 충돌 현상 | `--user-data-dir` 격리 옵션으로 독립 App Mode 구동 |
| **빌드 파일 락** | `build.ps1` 실행 중 `Remove-Item` 단계 실패 | 앱 실행 중 JAR 파일 잠금(Lock) 상태 | 재패키징 전 `OP_Certificate_Generator.exe` 프로세스 종료 필수 |

---

<div align="center">

**© 2026 Optilo Co., Ltd. — 내부 업무용 도구**

</div>
"""

out_path = r"c:\work\pdf-generator-v3\README.md"
with open(out_path, "w", encoding="utf-8") as f:
    f.write(content)

print(f"SUCCESS: README.md saved to {out_path}")
print(f"File size: {len(content)} chars")

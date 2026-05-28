<div align="center">

# DnDn

<img width="365" height="323" alt="Image" src="https://github.com/user-attachments/assets/c5cce926-7013-4f76-ad11-ac0a90afdfc0" />

### 건설 현장을 더 스마트하게, 더 안전하게

인력 배치, 공정 분석, 문서 자동화, ESG 지표까지<br/>
건설 현장 운영 데이터를 하나로 연결하는 통합 관리 플랫폼입니다.

<br/>

### 팀원

| 김민규 | 전민주 | 이한별 | 전성훈 | 최승우 |
| :---: | :---: | :---: | :---: | :---: |
| <img src="https://github.com/luel1018.png" width="96" alt="김민규"/> | <img src="https://github.com/minju0077.png" width="96" alt="전민주"/> | <img src="https://github.com/sole0714.png" width="96" alt="이한별"/> | <img src="https://github.com/1jshun.png" width="96" alt="전성훈"/> | <img src="https://github.com/sw-oo.png" width="96" alt="최승우"/> |
| [@luel1018](https://github.com/luel1018) | [@minju0077](https://github.com/minju0077) | [@sole0714](https://github.com/sole0714) | [@1jshun](https://github.com/1jshun) | [@sw-oo](https://github.com/sw-oo) |

<br/>

[![Java](https://img.shields.io/badge/Java-17-007396?style=for-the-badge&logo=openjdk&logoColor=white)](#기술-스택)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5.11-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)](#기술-스택)
[![Kubernetes](https://img.shields.io/badge/Kubernetes-326CE5?style=for-the-badge&logo=kubernetes&logoColor=white)](#배포)
[![Blue Green](https://img.shields.io/badge/Blue--Green-Deployment-00A878?style=for-the-badge)](#배포)

<br/>

[홈페이지](https://www.dndn24.kro.kr) ·
[Swagger UI](https://www.dndn24.kro.kr/api/swagger-ui/index.html) ·
[무중단 배포 시연](https://github.com/user-attachments/assets/9a31362a-4578-4c3a-af02-114a7b628da8)

</div>

<br/>

## 프로젝트 소개

DnDn은 건설 현장에서 발생하는 인력, 근태, 안전, 공정, 문서, 환경 데이터를 실시간으로 수집하고 관리하는 백엔드 플랫폼입니다.<br/>
현장 소장과 관리자는 근로자 현황, 게이트 혼잡도, 공정 진행률, 일일 보고, ESG 지표를 한 화면에서 확인하고 데이터 기반으로 의사결정할 수 있습니다.

<br/>

## 핵심 요약

| 영역         | 제공 기능                                |
|------------|--------------------------------------|
| **인력 관리**  | 근무자 등록, 안전사고 이력, 제재 이력 관리            |
| **근태 관리**  | 모바일 앱 기반 출퇴근 인식, 피로도 자동 산정, 근태 현황 조회 |
| **인력 배치**  | 구역별 직종 수요와 피로도를 반영한 인력 배치 추천         |
| **AI 공정표** | 마스터 공정표 업로드, AI 기반 일정·작업 항목 자동 추출    |
| **일정 관리**  | 작업 계획, 작업 지시, 공사 일보 작성과 이력 관리        |
| **현장 정보**  | 날씨·미세먼지 연동, ESG 지표 시각화, 현장 운영 대시보드   |

<br/>

## 기술 스택

### 백엔드

| 구분 | 기술 |
| --- | --- |
| 언어 | ![Java](https://img.shields.io/badge/Java_17-007396?style=flat-square&logo=openjdk&logoColor=white) |
| 프레임워크 | ![Spring Boot](https://img.shields.io/badge/Spring_Boot_3.5.11-6DB33F?style=flat-square&logo=springboot&logoColor=white) ![Spring Security](https://img.shields.io/badge/Spring_Security-6DB33F?style=flat-square&logo=springsecurity&logoColor=white) ![Spring Cloud](https://img.shields.io/badge/Spring_Cloud-6DB33F?style=flat-square&logo=spring&logoColor=white) |
| 데이터 | ![JPA](https://img.shields.io/badge/JPA-59666C?style=flat-square&logo=hibernate&logoColor=white) ![MariaDB](https://img.shields.io/badge/MariaDB-003545?style=flat-square&logo=mariadb&logoColor=white) ![Redis](https://img.shields.io/badge/Redis-DC382D?style=flat-square&logo=redis&logoColor=white) ![Elasticsearch](https://img.shields.io/badge/Elasticsearch-005571?style=flat-square&logo=elasticsearch&logoColor=white) |
| 메시징 | ![Kafka](https://img.shields.io/badge/Kafka-231F20?style=flat-square&logo=apachekafka&logoColor=white) |
| AI·파일 | ![OpenAI](https://img.shields.io/badge/OpenAI_API-412991?style=flat-square&logo=openai&logoColor=white) ![AWS S3](https://img.shields.io/badge/AWS_S3-569A31?style=flat-square&logo=amazons3&logoColor=white) ![Apache POI](https://img.shields.io/badge/Apache_POI-D22128?style=flat-square&logo=apache&logoColor=white) |
| API | ![Swagger](https://img.shields.io/badge/Swagger-85EA2D?style=flat-square&logo=swagger&logoColor=black) ![JWT](https://img.shields.io/badge/JWT-000000?style=flat-square&logo=jsonwebtokens&logoColor=white) |

### 인프라

| 구분 | 기술 |
| --- | --- |
| CI/CD | ![Jenkins](https://img.shields.io/badge/Jenkins-D24939?style=flat-square&logo=jenkins&logoColor=white) ![Kaniko](https://img.shields.io/badge/Kaniko-2496ED?style=flat-square&logo=docker&logoColor=white) ![Docker Hub](https://img.shields.io/badge/Docker_Hub-2496ED?style=flat-square&logo=docker&logoColor=white) |
| 실행 환경 | ![Docker](https://img.shields.io/badge/Docker-2496ED?style=flat-square&logo=docker&logoColor=white) ![Kubernetes](https://img.shields.io/badge/Kubernetes-326CE5?style=flat-square&logo=kubernetes&logoColor=white) |
| 게이트웨이 | ![Nginx Ingress](https://img.shields.io/badge/Nginx_Ingress-009639?style=flat-square&logo=nginx&logoColor=white) |
| 모니터링 | ![Actuator](https://img.shields.io/badge/Spring_Actuator-6DB33F?style=flat-square&logo=springboot&logoColor=white) ![Prometheus](https://img.shields.io/badge/Prometheus-E6522C?style=flat-square&logo=prometheus&logoColor=white) ![Grafana](https://img.shields.io/badge/Grafana-F46800?style=flat-square&logo=grafana&logoColor=white) |

<br/>

## 시스템 아키텍처

<img width="1611" height="1181" alt="Image" src="https://github.com/user-attachments/assets/fcf66e22-a5e0-4e42-9649-cdc7d59fd750" />

<br/>

## 모듈 구성

| 모듈 | 역할 |
| --- | --- |
| `dndn-core` | 인증, 근로자, 게이트, 인력 배치, 공정표, 작업 계획, 일보, ESG, 날씨 등 핵심 도메인 API |
| `dndn-document-management` | 문서 업로드, 문서 검색, Elasticsearch 연동, 문서 이벤트 처리 |
| `dndn-gateway` | Spring Cloud Gateway 기반 라우팅, Eureka 연동, JWT 처리 |
| `dndn-discovery` | Eureka Server 기반 서비스 디스커버리 |

<br/>

## 무중단 배포

DnDn 백엔드는 Kubernetes 환경에서 Nginx Ingress를 통해 외부 트래픽을 받고, Jenkins와 Kaniko를 이용해 Blue-Green 방식으로 배포됩니다.

```mermaid
Jenkins 빌드 시작
    ↓
Kaniko로 이미지 빌드 → Docker Hub Push
    ↓
현재 Active 색 감지 (Ingress service name 기준)
    ↓
비활성 Deployment에 새 이미지 set + replicas 확장
    ↓
rollout status 대기 (파드 준비 완료 확인)
    ↓
Ingress 전환 (backend-service-blue ↔ backend-service-green)
    ↓
이전 Deployment replicas=0 으로 축소
```

### Blue-Green을 선택한 이유

| 대상 | 선택 이유 |
| --- | --- |
| Core | 로그인, 인증, 프로젝트 등 사용자 흐름의 중심 API입니다. 새 버전이 정상 기동되기 전에 기존 버전이 내려가면 서비스 전체가 영향받기 때문에, inactive 환경에서 먼저 검증한 뒤 트래픽을 전환하는 Blue-Green 구조가 필요했습니다. |
| Document Management | MariaDB, Kafka, Elasticsearch, S3, Eureka 등 외부 의존성이 많아 배포 시 환경변수 누락, 인증서 Secret 누락, DB 접속 지연, Kafka 설정 문제 등이 발생할 수 있습니다. 새 버전을 기존 Pod에 바로 덮어쓰지 않고 inactive 버전에 먼저 올려 정상 여부를 확인한 뒤 트래픽을 넘기기 위해 Blue-Green을 선택했습니다. |


### 전환 방식

Ingress와 Gateway는 `blue`/`green` Deployment를 직접 바라보지 않습니다.  
항상 고정된 Kubernetes Service를 바라보며, 실제 버전 전환은 Service의 selector만 변경해 처리합니다.


---

<div align="center">

Copyright © 2026 Intelli_J Team. All rights reserved.

</div>

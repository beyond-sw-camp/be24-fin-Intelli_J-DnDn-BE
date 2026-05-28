<div align="center">

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
[무중단 배포 시연](https://github.com/user-attachments/assets/9a31362a-4578-4c3a-af02-114a7b628da8) ·
[API 명세서](https://1jshun.github.io/DnDn-Architecture/)

</div>

<br/>

## 프로젝트 소개

DnDn은 건설 현장에서 발생하는 인력, 근태, 안전, 공정, 문서, 환경 데이터를 실시간으로 수집하고 관리하는 플랫폼입니다.<br/>
현장 소장과 관리자는 근로자 현황, 게이트 혼잡도, 공정 진행률, 일일 보고, ESG 지표를 한 화면에서 확인하고 데이터 기반으로 의사결정할 수 있습니다.

<br/>

## 기술 스택

### 백엔드

| 구분 | 기술 |
| --- | --- |
| 언어 | ![Java](https://img.shields.io/badge/Java_17-007396?style=flat-square&logo=openjdk&logoColor=white) |
| 프레임워크 | ![Spring Boot](https://img.shields.io/badge/Spring_Boot_3.5.11-6DB33F?style=flat-square&logo=springboot&logoColor=white) ![Spring Security](https://img.shields.io/badge/Spring_Security-6DB33F?style=flat-square&logo=springsecurity&logoColor=white) ![Spring Cloud](https://img.shields.io/badge/Spring_Cloud-6DB33F?style=flat-square&logo=spring&logoColor=white) |
| 데이터 | ![JPA](https://img.shields.io/badge/JPA-59666C?style=flat-square&logo=hibernate&logoColor=white) ![MariaDB](https://img.shields.io/badge/MariaDB-003545?style=flat-square&logo=mariadb&logoColor=white) ![Redis](https://img.shields.io/badge/Redis-DC382D?style=flat-square&logo=redis&logoColor=white) ![Elasticsearch](https://img.shields.io/badge/Elasticsearch-005571?style=flat-square&logo=elasticsearch&logoColor=white) |
| 메시징 | ![Kafka](https://img.shields.io/badge/Kafka-231F20?style=flat-square&logo=apachekafka&logoColor=white) |
| AI·파일 | ![OpenAI](https://img.shields.io/badge/OpenAI_API-412991?style=flat-square&logo=openai&logoColor=white) ![AWS S3](https://img.shields.io/badge/AWS_S3-569A31?style=flat-square&logo=amazons3&logoColor=white) ![Apache POI](https://img.shields.io/badge/Apache_POI-D22128?style=flat-square&logo=apache&logoColor=white) ![KMA](https://img.shields.io/badge/KMA_Weather_API-2F80ED?style=flat-square) ![AirKorea](https://img.shields.io/badge/AirKorea_API-00A3E0?style=flat-square) |
| API | ![Swagger](https://img.shields.io/badge/Swagger-85EA2D?style=flat-square&logo=swagger&logoColor=black) ![JWT](https://img.shields.io/badge/JWT-000000?style=flat-square&logo=jsonwebtokens&logoColor=white) |

<table>
<tr>
<td valign="top" width="58%">

### 인프라

| 구분 | 기술 |
| --- | --- |
| CI/CD | ![Jenkins](https://img.shields.io/badge/Jenkins-D24939?style=flat-square&logo=jenkins&logoColor=white) ![Kaniko](https://img.shields.io/badge/Kaniko-2496ED?style=flat-square&logo=docker&logoColor=white) ![Docker Hub](https://img.shields.io/badge/Docker_Hub-2496ED?style=flat-square&logo=docker&logoColor=white) |
| 실행 환경 | ![Docker](https://img.shields.io/badge/Docker-2496ED?style=flat-square&logo=docker&logoColor=white) ![Kubernetes](https://img.shields.io/badge/Kubernetes-326CE5?style=flat-square&logo=kubernetes&logoColor=white) |
| 게이트웨이 | ![Nginx Ingress](https://img.shields.io/badge/Nginx_Ingress-009639?style=flat-square&logo=nginx&logoColor=white) |
| 모니터링 | ![Actuator](https://img.shields.io/badge/Spring_Actuator-6DB33F?style=flat-square&logo=springboot&logoColor=white) ![Prometheus](https://img.shields.io/badge/Prometheus-E6522C?style=flat-square&logo=prometheus&logoColor=white) ![Grafana](https://img.shields.io/badge/Grafana-F46800?style=flat-square&logo=grafana&logoColor=white) |
| Storage Class | ![Longhorn](https://img.shields.io/badge/Longhorn-FFB000?style=flat-square&logo=linuxfoundation&logoColor=white) |

</td>
<td valign="top" width="42%">

### 협업

| 분류 | 기술 |
| --- | --- |
| Version Control | ![Git](https://img.shields.io/badge/Git-F05032?style=flat-square&logo=git&logoColor=white) ![GitHub](https://img.shields.io/badge/GitHub-181717?style=flat-square&logo=github&logoColor=white) |
| API Test | ![Postman](https://img.shields.io/badge/Postman-FF6C37?style=flat-square&logo=postman&logoColor=white) |
| Design / Docs | ![Figma](https://img.shields.io/badge/Figma-F24E1E?style=flat-square&logo=figma&logoColor=white) ![Notion](https://img.shields.io/badge/Notion-000000?style=flat-square&logo=notion&logoColor=white) |
| Communication | ![Discord](https://img.shields.io/badge/Discord-5865F2?style=flat-square&logo=discord&logoColor=white) |

</td>
</tr>
</table>

<br/>

## 시스템 아키텍처

<img width="959" height="993" alt="imaasdge" src="https://github.com/user-attachments/assets/8da5c9a5-f17f-425b-9f92-1df84e92c927" />

<br/>

## 프로젝트 문서

프로젝트 기획서, WBS, 요구사항 명세서, 화면 설계서, API 영상, ERD 등 상세 문서는 Wiki에서 확인할 수 있습니다.

| 문서 | 링크 |
| --- | --- |
| DnDn Backend Wiki | [Wiki 바로 가기](https://github.com/beyond-sw-camp/be24-fin-Intelli_J-DnDn-BE/wiki) |

</br>

## 모듈 구성

| 모듈 | 역할 |
| --- | --- |
| `dndn-core` | 인증, 근로자, 게이트, 인력 배치, 공정표, 작업 계획, 일보, ESG, 날씨 등 핵심 도메인 API |
| `dndn-document-management` | 문서 업로드, 문서 검색, Elasticsearch 연동, 문서 이벤트 처리 |
| `dndn-gateway` | Spring Cloud Gateway 기반 라우팅, Eureka 연동, JWT 처리 |
| `dndn-discovery` | Eureka Server 기반 서비스 디스커버리 |

<br/>

## 백엔드 핵심 설계

| 항목 | 적용 기술 | 도입 이유 |
| --- | --- | --- |
| [인증](https://github.com/beyond-sw-camp/be24-fin-Intelli_J-DnDn-BE/wiki/02.-%EC%9D%B8%EC%A6%9D%EA%B3%BC-%EC%9D%B8%EA%B0%80) | JWT, Spring Security | 서버 세션을 사용하지 않는 Stateless 인증 구조를 구성했습니다. Access Token에는 사용자 식별자와 역할 정보를 담고, 요청마다 JWT를 검증해 인증 상태를 복원합니다. |
| [인가](https://github.com/beyond-sw-camp/be24-fin-Intelli_J-DnDn-BE/wiki/02.-%EC%9D%B8%EC%A6%9D%EA%B3%BC-%EC%9D%B8%EA%B0%80) | RBAC + 도메인 권한 검증 | `ADMIN`, `HEADQUARTOR`, `SITE_MANAGER`, `SITE_DIRECTOR`, `SECTION_LEADER`, `SECTION_SUPERVISOR` 역할 기반 접근 제어를 적용했습니다. 일부 기능은 역할뿐 아니라 현장 코드와 공종 범위까지 확인해 실제 업무 권한에 맞게 접근을 제한합니다. |
| API Gateway | Spring Cloud Gateway GlobalFilter | `/api/msa/**` 요청은 Gateway에서 JWT를 먼저 검증하고, 검증된 사용자 정보를 `X-User-Idx`, `X-User-Role`, `X-User-LoginId` 헤더로 하위 서비스에 전달합니다. |
| [Kafka](https://github.com/beyond-sw-camp/be24-fin-Intelli_J-DnDn-BE/wiki/03.-%EB%AC%B8%EC%84%9C%EA%B4%80%EB%A6%AC-MSA%EC%99%80-Kafka) | 서비스 간 비동기 이벤트 연동 | Core에서 문서 업로드, 작업 지시 변경, 공사 일보 변경 이벤트를 발행하고 Document Management가 이를 소비해 문서 검색 인덱스를 갱신합니다. Core 트랜잭션과 검색 인덱싱을 분리해 장애 전파를 줄였습니다. |
| [Elasticsearch](https://github.com/beyond-sw-camp/be24-fin-Intelli_J-DnDn-BE/wiki/04.-Elasticsearch-%EB%8F%84%EC%9E%85) | 문서 통합 검색 | 공정표, 작업 지시, 공사 일보 등 문서성 데이터를 여러 필드 기준으로 검색해야 했기 때문에 Elasticsearch를 도입했습니다. 키워드 검색은 ES를 사용하고, ES 장애 시 RDB 검색으로 fallback하도록 구성했습니다. |
| [Redis](https://github.com/beyond-sw-camp/be24-fin-Intelli_J-DnDn-BE/wiki/05.-Redis-%ED%99%9C%EC%9A%A9) | 캐시, 분산락 | ESG 대시보드처럼 반복 조회되는 데이터를 캐싱하고, 다중 Pod 환경에서 스케줄러가 중복 실행되지 않도록 Redisson 기반 분산락을 사용했습니다. |
| [Batch](https://github.com/beyond-sw-camp/be24-fin-Intelli_J-DnDn-BE/wiki/06.-Batch%EC%99%80-%EC%8A%A4%EC%BC%80%EC%A4%84%EB%9F%AC) | Kubernetes CronJob / Job Trigger | 인력 동기화처럼 오래 걸리거나 주기적으로 실행되는 작업은 API 서버 내부 요청 흐름과 분리했습니다. Core API에서 Kubernetes CronJob 기반 Job을 수동 트리거할 수 있도록 구성했습니다. |

<br/>

## 무중단 배포

DnDn 백엔드는 Kubernetes 환경에서 Nginx Ingress를 통해 외부 트래픽을 받고, Jenkins와 Kaniko를 이용해 Blue-Green 방식으로 배포됩니다.

```text
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

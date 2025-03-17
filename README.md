## 목표: 사용자가 앱 별로 알림 수신 모드를 직접 설정할 수 있는 앱 개발, 사용자가 알림 수신 모드를 설정하는 이유와 개인화된 알림 수신 모드의 효과를 조사
### 작동 영상
<img src= https://github.com/user-attachments/assets/db16cf75-d4a0-453f-b474-880609c2ea4b>
<br/> 
<br/> 사용자들은 ‘앱 및 키워드 추가’버튼을 클릭
<br/> 최근 알림을 받은 앱 리스트를 알림 내용과 함께 보여줌
<br/> 3가지 수신 모드에 앱을 원하는 대로 설정
<br/> 하단의 ‘N개 알림 받기’ 버튼을 클릭하면,요청시 받기로 설정한 앱들의 알림을 한 번에 수신할 수 있음
<br/>
<br/>

### Background
과도한 알림과 시기적절하지 않은 알림
<br/> 사용자의 작업을 방해하고 생산성을 저해 + 정신적 스트레스를 증가
<br/> Ex) 알림을 놓칠 것에 대한 두려움 FoMO (Fear fo Missing Out)로 인하여 핸드폰을 자주 확인
<br/> 사용자들은 이러한 부정적 요소를 줄이고자 DND(Do Not Disturb)모드와 무음 모드 사용
<br/> But, 사용자의 상황 맥락을 간과하고 번거로운 수동 설정을 요구
<br/> 사용자가 각 앱으로부터 알림을 받고자 하는 시점을 수동으로 정의할 수 있도록 하는 시스템을 개발
<br/> 시스템은 푸시-풀(push-pull) 프레임워크에 기반하여 세 가지 알림 수신 모드 제공
<br/>
<br/> 

### Research Question
RQ1: 사용자 정의 알림 수신 모드는 사용자의 스마트폰 사용 및 알림 소비 행동에 어떤 영향을 미치는가?
<br/> RQ2: 사용자가 개인화된 알림 설정을 구성할 때 고려하는 요인은 무엇이며, 이러한 요인은 개인마다 어떻게 다르게 나타나는가?
<br/> RQ3: 사용자 정의 알림 수신 모드는 사용자의 정신 건강에 어떤 영향을 미치는가?
<br/> 

### Methodology: 알림 수신 모드 정의
즉시 받기: 기존과 동일하게, 스마트폰 알림이 발생하면 사용자에게 해당 알림을 바로 전송
<br/> 사용 중 받기: 알림을 즉시 전송하지 않고, 사용자가 스마트폰의 화면을 켜면 알림들을 전송
<br/> 요청 시 받기: 알림을 즉시 전송하지 않고, 사용자가 원할 때 특정 버튼을 누르면 쌓여 있던 알림들을 전송
<br/>
<br/> <img src=https://github.com/user-attachments/assets/7b0284bf-d00b-4056-ae63-8af4e46b31bb/>
<br/> 

### 알림커스터마이징 앱 개발
<br/><img src=https://github.com/user-attachments/assets/a26aba14-a525-476b-87f6-3ec868768c56/>
<br/> 
<br/> Kotlin(버전 1.9)을 이용한 Android 13 기반의 Android 모바일 애플리케이션을 개발
<br/> 트리거된 후 원본 알림을 일시 중단하는 것은 불가능
<br/> 이러한 문제를 해결하기 위해 NotificationListenerService API를 사용하여 원래의 알림을 삭제
<br/> 제목, 텍스트, 아이콘, 알림 ID와 같은 정보를 복제
<br/> ‘CustomNotif’라는 앱을 사용하여 모바일 애플리케이션을 통해 알림을 전송

<br/> - 데이터 수집 방법
<br/> UsageStatsManager API를 사용하여 화면을 키거나 상단바를 내려 알림을 확인하는 로그데이터를 수집
<br/> NotificationListenerService API를 사용하여 사용자가 알림을 받아서 어떻게 처리하는지와 같은 알림 컨텍스트 데이터를 수집
<br/> 이렇게 수집된 데이트는 실시간으로 구글 파이어베이스에 저장
<br/> 프라이버시 보호를 위해 앱의 실행기록과 대화 내용은 수집하지 않았으며, 알림 텍스트는 앞뒤 4글자씩만 남기고 암호화(*) 처리
<br/> 

### 연구 Process
<br/><img src=https://github.com/user-attachments/assets/07874421-b9c3-4196-9d9f-cf5d548f2734/>
<br/> 
<br/> 

### 연구 결과
#### 알림 개수 감소
1,205.3(SD=766.3) -> 1,043.5 (SD=567.8)
<br/> - 동일한 알림 ID를 가진 경우, 기존 알림 위에 새로운 알림이 덮어씌워져 최근 알림만 표시 (P08: 카카오톡  사용중 받기)
<br/> - 일부 참가자(N=5)는 실험 기간 동안 단 한 번도 버튼을 누르지 않음. "이미 중요한 앱들은 다른 모드로 설정했기 때문에 굳이 버튼을 누를 필요가 없었어요."
<br/> 

#### 핸드폰 사용 & 알림 확인 행동 변화
<br/> 휴대폰 사용시간: 변화 없음
<br/> 휴대폰 확인 빈도: 변화 없음
<br/> 알림 확인 빈도: 721.2 (SD=518.9)  648.2 (SD=479.0)
<br/> 쌓여있는 알림 개수: 1.66(SD=0.33)  1.58 (SD=0.35)
<br/>
<br/> 사용자 맞춤형 알림 설정이 보다 효율적인 알림 경험 제공
<br/> 

#### 알림 수신모드 설정
<br/><img src=https://github.com/user-attachments/assets/4510943f-263b-483b-9169-56d64ed83e9c/>
<br/> 개인적인 선호에 따라 각 애플리케이션을 세 가지 알림 수신 모드로 다양하게 설정
<br/> Ex) 인스타그램: 즉시 알림(N=11), 사용 중 알림(N=2), 요청 시 알림(N=4)
<br/> <img src=https://github.com/user-attachments/assets/bbb651da-e5cf-4960-bc77-2bf476256231/>
<br/> 동일한 앱이나 같은 카테고리의 앱일지라도 알림 모드 설정 시 중요도나 긴급도를 다르게 평가
<br/> 

##### 알림 수신모드 설정 이유
Immediate
<br/> • High Urgency or Time-Sensitivity
<br/> • Desire to View Content as Soon as Possible
<br/> • Fear of Missing Critical Notifications
<br/>
<br/> While in Use
<br/> • Moderate Urgency but Not Critical
<br/> • Timely Updates Preferred When Actively Using the Phone
<br/> • Notifications Relevant Only During Smartphone Usage
<br/>
<br/> On Demand
<br/> • Low or No Urgency
<br/> • Non-critical Notifications
<br/> • Unwanted Notifications
<br/> 

#### 멘탈지수 변화
<br/><img src=https://github.com/user-attachments/assets/ce0b55e6-fc9c-4701-a119-30cf30ed4bef/>
<br/>MPA (모바일 중독): 4.70 -> 3.97
<br/>IFO (정보 과부화): 4.17 -> 3.09
<br/>CO (의사소통 과부화): 4.26 -> 2.91
<br/>NPG(알림 피로도): 3.36 -> 2.69
<br/>모두 유의미하게 감소
<br/> 

#### 인터뷰를 통한 일상적인 습관 변화
"예전에는 아침에 눈을 뜨자마자 알림을 확인하고 삭제하면서 하루를 시작했어요. 하지만 이제는 더 이상 그럴 필요가 없어졌어요. 그리고 내가 실제로 보고 싶은 알림들이 쓸데없는 알림들에 묻히지 않게 되어서 더 좋아요."
<br/> 
<br/> 




## 결과물
[SCIE 저널 게제](https://ieeexplore.ieee.org/document/10916668)

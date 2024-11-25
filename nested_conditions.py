# 중첩 조건문: 나이와 학생 여부에 따라 메시지 출력
age = 16
is_student = True

if age < 18: # 첫 번째 조건
    if is_student: # 두 번째 조건
        print("You are a minor and also a student.")
    else:
        print("You are a minor but not a student.")
else: 
    print("You are an adult.")


#논리 연산자 사용
age = 16
is_student = True

if age < 18 and is_student: #나이가 18 미만이고 학생일 경우
    print("You are a minor and also a student.")
elif age < 18 and not is_student: #나이가 18 미만이고 학생이 아닐 경우
    print("You are a minor but not a student.")
else: 
    print("You are an adult.")


#사용자 입력 받기
age = int(input("Enter your age: ")) #나이를 숫자로 입력하기
is_student = input("Are you a student? (yes/no): ").lower() == "yes" #학생 여부 확인

#조건문 실행
if age < 18:
    if is_student:
        print("You are a minor and also a student.")
    else:
        print("You are a minor but not a student.")
else:
    print("You are an adult.")


#나이를 입력받기(숫자만 허용)
while True:
    age_input = input("Enter your age: ") # 사용자 입력
    if age_input.isdigit(): # 숫자인지 확인
        age = int(age_input) # 정수로 변환
        break # 유효한 입력일 경우 반복문 종료
    else:
        print("Please enter a valid number.") # 오류 메시지

# 학생 여부 입력받기 ("yes/no만 허용")
while True:
    is_student_input = input("Are you a student? (yes/no): ").lower() # 사용자 입력 (소문자로 변환)
    if is_student_input in ["yes", "no"]: # yes 또는 no만 허용
        is_student = is_student_input == "yes" #"yes"면 True, "no"면 False
        break # 유효한 입력일 경우 반복문 종료
    else:
        print("Please answer with 'yes' or 'no'.") # 오류 메시지

#결과 출력
if age < 18:
    if is_student: 
        print("You are a minor and also a student.")
    else:
        print("You are a minot but not a student.")
else: print("You are an adult.")
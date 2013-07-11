mov $1, r0
sys write
hello
6

mov $hello, r0
mov $62510, r1
mov r1, 2(r0)
/mov r1, (r0)+
/mov r1, (r0)+
/mov $61506, r1
/mov r1, -(r0)
mov $1, r0
sys write
hello
6

mov $0, r0
sys exit

.data
hello: <hello\n> 

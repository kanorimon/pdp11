mov $1, r0
sys write
hello
6

mov $0, r0
sys exit

.data
hello: <hello\n> 

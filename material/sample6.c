main() {
	printo(012345);
}

printo(v) {
	int i;
	putchar(v < 0 ? '1' : '0');
	for(i = 0; i < 5; i++)
	{
		putchar(((v >> 12) & 7) + '0');
		v =<< 3;
 	}
}
